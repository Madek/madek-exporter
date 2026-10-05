(ns madek.exporter.connection
  (:require
   [clj-http.client :as http-client]
   [clj-time.core :as time]
   [clj-time.format :as time-format]
   [clojure.string :as string]
   [json-roa.client.core :as roa]
   [logbug.catcher :as catcher]
   [logbug.debug :as debug]
   [madek.exporter.state :as state]
   [madek.exporter.utils :as utils]
   [taoensso.timbre :as timbre :refer [info debug]]))

;;; auth-info probes (api + api-v2) ############################################

(defn- ok-status? [status]
  (and (integer? status) (>= status 200) (< status 300)))

(defn- token-auth? [http-options]
  (boolean
   (some-> http-options :headers (get "authorization")
           string/lower-case
           (string/starts-with? "token "))))

(defn- basic-auth-only? [http-options]
  (and (boolean (:basic-auth http-options))
       (not (token-auth? http-options))))

(defn- identity-fields [m]
  (when (map? m)
    (let [get* (fn [k]
                 (or (get m k)
                     (get m (name k))))]
      (into {}
            (remove (comp nil? val)
                    {:type (get* :type)
                     :id (get* :id)
                     :login (get* :login)
                     :email_address (get* :email_address)
                     :authentication-method (get* :authentication-method)})))))

(defn- has-identity? [auth-info]
  (boolean
   (and (map? auth-info)
        (or (:login auth-info)
            (:email_address auth-info)
            (:id auth-info)
            (:type auth-info)))))

(defn- failure-reason [status error]
  (cond
    (and (string? error)
         (re-find #"(?i)basic|api-client|token or session" error))
    :unsupported-auth

    (nil? status)
    :unreachable

    (#{401 403} status)
    :auth-failed

    (#{404 405} status)
    :not-found

    (>= status 500)
    :unreachable

    :else
    :auth-failed))

(defn- probe-failure
  ([status error]
   (cond-> {:available false
            :reason (failure-reason status error)}
     status (assoc :status status)
     error (assoc :error error))))

(defn- probe-success [status auth-info]
  {:available true
   :status status
   :auth-info auth-info})

(defn availability-summary
  "Derive :both | :api | :api-v2 | :neither from per-API probe maps."
  [api-status]
  (let [api? (boolean (get-in api-status [:api :available]))
        v2? (boolean (get-in api-status [:api-v2 :available]))]
    (cond
      (and api? v2?) :both
      api? :api
      v2? :api-v2
      :else :neither)))

(defn api-mode-allowed?
  "True when the selected api-mode is covered by the probe results.
  Modes: :api | :api-v2 | :both (also accepts string names)."
  [api-status api-mode]
  (let [mode (keyword (or api-mode :api))
        api? (boolean (get-in api-status [:api :available]))
        v2? (boolean (get-in api-status [:api-v2 :available]))]
    (case mode
      :api api?
      :api-v2 v2?
      :both (and api? v2?)
      false)))

(defn- normalize-api-mode [api-mode]
  (let [mode (keyword (or api-mode :api))]
    (if (#{:api :api-v2 :both} mode) mode :api)))

(defn probe-api
  "GET {base}/api/auth-info via JSON-ROA. Never throws; one failure does not
  affect the api-v2 probe."
  [base-url live-http-options]
  (try
    (let [api-root (roa/get-root (str base-url "/api/")
                                 :default-conn-opts live-http-options)
          response (-> api-root (roa/relation :auth-info) (roa/get {}))
          status (:status response)
          data (try (identity-fields (roa/data response))
                    (catch Exception _ nil))]
      (if (and (ok-status? status) (has-identity? data))
        (probe-success status data)
        (probe-failure status
                       (or (some-> response :body :message)
                           (some-> data :message)
                           "Authentication failed"))))
    (catch Exception e
      (let [status (:status (ex-data e))]
        (timbre/warn e "api auth-info probe failed")
        (probe-failure status
                       (or (ex-message e)
                           "Could not reach /api"))))))

(defn probe-api-v2
  "GET {base}/api-v2/auth-info/ (plain HTTP). Requires token or session;
  Basic/ApiClient is not supported. Never throws."
  [base-url live-http-options http-options]
  (cond
    (not (utils/authenticated-http-options? http-options))
    (probe-failure nil "No credentials provided")

    (basic-auth-only? http-options)
    (probe-failure nil
                   (str "api-v2 requires token or session; "
                        "Basic/ApiClient auth is not supported"))

    :else
    (try
      (let [url (str base-url "/api-v2/auth-info/")
            response (http-client/get
                      url
                      (merge live-http-options
                             {:as :json
                              :coerce :always
                              :throw-exceptions false}))
            status (:status response)
            body (:body response)
            data (identity-fields body)]
        (if (and (ok-status? status) (has-identity? data))
          (probe-success status data)
          (probe-failure status
                         (or (when (map? body)
                               (or (get body :message)
                                   (get body "message")
                                   (get body :msg)
                                   (get body "msg")))
                             "Authentication failed"))))
      (catch Exception e
        (let [status (:status (ex-data e))]
          (timbre/warn e "api-v2 auth-info probe failed")
          (probe-failure status
                         (or (ex-message e)
                             "Could not reach /api-v2")))))))

(defn probe-apis
  "Probe /api and /api-v2 auth-info independently. Adds :availability summary."
  [base-url live-http-options http-options]
  (let [status {:api (probe-api base-url live-http-options)
                :api-v2 (probe-api-v2 base-url live-http-options http-options)}]
    (assoc status :availability (availability-summary status))))

;;; legacy helper (export credential check) ###################################

(defn auth-info [api-entry-point api-http-opts]
  (let [response (-> (roa/get-root api-entry-point :default-conn-opts api-http-opts)
                     (roa/relation :auth-info)
                     (roa/get {}))]
    (debug (-> response roa/data))
    response))

;;; connect / disconnect ######################################################

(defn connect-to-madek-server [request]
  (catcher/snatch
   {:return-fn (fn [e]
                 (timbre/error e "Unexpected error while connecting to Madek")
                 {:status 500
                  :body {:message "Connection failed. Please try again."}})}
   (try (let [connect-body (:body request)
              url (some-> connect-body :url utils/presence)
              login (some-> connect-body :login utils/presence)
              password (some-> connect-body :password utils/presence)
              api-token (some-> connect-body :api-token utils/presence)
              _ (debug 'connect-request
                       {:path (:uri request)
                        :has-body (map? connect-body)
                        :has-url (boolean url)
                        :has-login (boolean login)
                        :has-password (boolean password)
                        :has-api-token (boolean api-token)})
              _ (when-not (map? connect-body)
                  (throw (ex-info "invalid connect payload" {:status 422 :message "Expected JSON object body"})))
              _ (when-not url
                  (throw (ex-info "missing url" {:status 422 :message "Missing required field: url"})))
              _ (when (and login (not password))
                  (throw (ex-info "invalid credentials" {:status 422
                                                          :message "Password is required when login is provided."})))
              http-options (utils/options-to-http-options connect-body)
              live-http-options (utils/with-http-pool http-options)
              _ (debug 'http-options
                       (cond-> http-options
                         (get-in http-options [:headers "authorization"])
                         (assoc-in [:headers "authorization"] "***redacted***")
                         (:basic-auth http-options)
                         (assoc :basic-auth "***redacted***")))
              api-status (if (utils/authenticated-http-options? http-options)
                           (probe-apis url live-http-options http-options)
                           nil)
              api-mode (normalize-api-mode (:api-mode connect-body))
              _ (debug 'api-status api-status)
              _ (debug 'api-mode api-mode)]
          (cond
            (not api-status)
            {:status 422
             :body {:message (str "no auth-info response, "
                                  "check your connection parameters.")}}

            (not (api-mode-allowed? api-status api-mode))
            {:status 422
             :body {:message (str "Selected API mode '" (name api-mode)
                                  "' is not available with the current credentials or host.")
                    :api-status api-status
                    :api-mode api-mode
                    :availability (:availability api-status)}}

            ;; Export still uses /api (JSON-ROA); require v1 auth until api-v2 export ships.
            (not (get-in api-status [:api :available]))
            (let [api-probe (:api api-status)
                  probe-status (:status api-probe)
                  status (cond
                           (nil? probe-status) 502
                           (ok-status? probe-status) 401
                           :else probe-status)]
              {:status status
               :body {:message (or (:error api-probe) "Authentication failed")
                      :api-status api-status
                      :availability (:availability api-status)}})

            :else
            (let [auth-info (get-in api-status [:api :auth-info])]
              (swap! state/db
                     (fn [db conn-params]
                       (assoc-in db [:connection] conn-params))
                     (merge
                      (select-keys connect-body [:url])
                      {:http-options http-options
                       :auth-info auth-info
                       :api-status api-status
                       :api-mode api-mode
                       :availability (:availability api-status)}
                      (select-keys auth-info [:login :email_address])))
              {:status 202
               :body {:api-status api-status
                      :api-mode api-mode
                      :availability (:availability api-status)}})))
        (catch Exception e
          (let [{:keys [status message]} (ex-data e)]
            (timbre/error e "Failed to connect to Madek")
            (cond
              (= status 401)
              {:status 401
               :body {:message "Authentication failed. Check your credentials."}}

              (= status 422)
              {:status 422
               :body {:message (or message "Invalid connect request.")}}

              (integer? status)
              {:status 422
               :body {:message (str "Could not connect to a Madek API at this URL. "
                                    "Check the Madek base URL.")}}

              :else
              {:status 502
               :body {:message (str "Could not reach the Madek server. "
                                    "Check the URL and your network connection.")}}))))))

(defn disconnect [_]
  (swap! state/db assoc-in [:connection] {}))

;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'ring.middleware.resource)
;(debug/debug-ns *ns*)
