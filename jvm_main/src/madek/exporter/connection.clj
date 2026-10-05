(ns madek.exporter.connection
  (:require
   [cheshire.core :as cheshire]
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

(defn- http-status-from-exception [e]
  (or (:status (ex-data e))
      (some-> e ex-data :object :status)
      (when-let [[_ code] (re-find #"(?i)status\s+(\d{3})"
                                   (or (ex-message e) ""))]
        (try (Integer/parseInt code)
             (catch Exception _ nil)))))

(defn- headers-from-exception [e]
  (or (:headers (ex-data e))
      (some-> e ex-data :object :headers)))

(defn- header-value [headers header-name]
  (when (map? headers)
    (or (get headers header-name)
        (get headers (string/lower-case header-name))
        (get headers (keyword header-name))
        (get headers (keyword (string/lower-case header-name)))
        (some (fn [[k v]]
                (when (and (string? k)
                           (= (string/lower-case k)
                              (string/lower-case header-name)))
                  v))
              headers))))

(defn- cloudflare-server? [headers]
  (boolean
   (when-let [server (header-value headers "Server")]
     (re-find #"(?i)cloudflare" (str server)))))

(def ^:private cloudflare-blocked-message
  "Requests are blocked, try using a VPN")

(defn- cloudflare-blocked? [status headers]
  (and (= 403 status) (cloudflare-server? headers)))

(defn- truncate-message [msg]
  (let [trimmed (string/replace (str msg) #"\s+" " ")]
    (if (> (count trimmed) 180)
      (str (subs trimmed 0 177) "...")
      trimmed)))

(defn- probe-error-message [status raw-message fallback]
  (cond
    (= 401 status) "Authentication failed (HTTP 401)"
    (= 403 status) "Forbidden (HTTP 403)"
    (#{404 405} status) "API endpoint not found on this host"
    (and (integer? status) (>= status 500))
    (str "Server error (HTTP " status ")")
    (and (integer? status) (not (ok-status? status)))
    (str "HTTP " status)
    (string? raw-message) (truncate-message raw-message)
    :else fallback))

(defn- failure-reason [status error]
  (cond
    (and (string? error)
         (re-find #"(?i)basic|api-client|token or session" error))
    :unsupported-auth

    (and (string? error)
         (re-find #"(?i)blocked.*VPN|cloudflare" error))
    :blocked

    (nil? status)
    :unreachable

    (#{401 403} status)
    :auth-failed

    (#{404 405} status)
    :not-found

    (and (integer? status) (>= status 500))
    :unreachable

    :else
    :auth-failed))

(defn- probe-failure
  ([status error]
   (probe-failure status error nil))
  ([status error headers]
   (let [cf? (cloudflare-blocked? status headers)
         error* (if cf?
                  cloudflare-blocked-message
                  error)
         reason (if cf? :blocked (failure-reason status error*))]
     (cond-> {:available false
              :reason reason}
       status (assoc :status status)
       error* (assoc :error error*)
       cf? (assoc :server "cloudflare")))))

(defn- probe-success [status auth-info]
  {:available true
   :status status
   :auth-info auth-info})

(defn- parse-json-body [body]
  (when (and (string? body)
             (re-find #"^\s*[\{\[]" body))
    (try
      (cheshire/parse-string body true)
      (catch Exception _ nil))))

(defn probe-api
  "GET {base}/api/auth-info via JSON-ROA. Never throws; one failure does not
  affect the api-v2 probe."
  [base-url live-http-options]
  (try
    (let [api-root (roa/get-root (str base-url "/api/")
                                 :default-conn-opts live-http-options)
          response (-> api-root (roa/relation :auth-info) (roa/get {}))
          status (:status response)
          headers (:headers response)]
      (if-not (ok-status? status)
        (probe-failure status
                       (probe-error-message status nil "Authentication failed")
                       headers)
        (let [data (try (identity-fields (roa/data response))
                        (catch Exception _ nil))]
          (if (has-identity? data)
            (probe-success status data)
            (probe-failure status "Authentication failed" headers)))))
    (catch Exception e
      (let [status (http-status-from-exception e)
            headers (headers-from-exception e)]
        (timbre/warn e "api auth-info probe failed")
        (probe-failure status
                       (probe-error-message status nil "Could not reach /api")
                       headers)))))

(defn probe-api-v2
  "GET {base}/api-v2/auth-info/ (plain HTTP). Requires token or session;
  Basic/ApiClient is not supported. Never throws.

  Uses :throw-exceptions false and decides availability from HTTP status
  before parsing the body as JSON."
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
                             {:as :text
                              :throw-exceptions false
                              :coerce :false}))
            status (:status response)
            headers (:headers response)]
        (if-not (ok-status? status)
          (probe-failure status
                         (probe-error-message status nil "Could not reach /api-v2")
                         headers)
          (let [raw-body (cond
                           (string? (:body response)) (:body response)
                           (nil? (:body response)) nil
                           :else (str (:body response)))
                data (identity-fields (parse-json-body raw-body))]
            (if (has-identity? data)
              (probe-success status data)
              (probe-failure status "Authentication failed" headers)))))
      (catch Exception e
        (let [status (http-status-from-exception e)
              headers (headers-from-exception e)]
          (timbre/warn e "api-v2 auth-info probe failed")
          (probe-failure status
                         (probe-error-message status nil
                                              "Could not reach /api-v2")
                         headers))))))

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
        available? (fn [k]
                     (boolean (or (get-in api-status [k :available])
                                  (get-in api-status [(name k) :available])
                                  (get-in api-status [k "available"]))))
        api? (available? :api)
        v2? (available? :api-v2)]
    (case mode
      :api api?
      :api-v2 v2?
      :both (and api? v2?)
      false)))

(defn- normalize-api-mode [api-mode]
  (let [mode (keyword (or api-mode :api))]
    (if (#{:api :api-v2 :both} mode) mode :api)))

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

(defn- cloudflare-blocked-status? [api-status]
  (boolean
   (some (fn [k]
           (let [p (get api-status k)]
             (or (= :blocked (:reason p))
                 (= "blocked" (:reason p))
                 (= "cloudflare" (:server p)))))
         [:api :api-v2])))

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
              ;; Provisional mode until the user picks one after login (UI).
              api-mode (normalize-api-mode (or (:api-mode connect-body) :api))
              _ (debug 'api-status api-status)
              _ (debug 'api-mode api-mode)]
          (cond
            (not api-status)
            {:status 422
             :body {:message (str "no auth-info response, "
                                  "check your connection parameters.")}}

            ;; Export still uses /api (JSON-ROA); require v1 auth until api-v2 export ships.
            (not (get-in api-status [:api :available]))
            (let [api-probe (:api api-status)
                  probe-status (:status api-probe)
                  status (cond
                           (nil? probe-status) 502
                           (ok-status? probe-status) 401
                           :else probe-status)
                  message (cond
                            (cloudflare-blocked-status? api-status)
                            cloudflare-blocked-message

                            (#{401 403} status)
                            "Authentication failed. Check your credentials."

                            (utils/presence (:error api-probe))
                            (:error api-probe)

                            :else "Authentication failed")]
              {:status status
               :body {:message message
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

(defn patch-api-mode
  "Set :api-mode on an existing connection after login, gated by probe results."
  [request]
  (catcher/snatch
   {:return-fn (fn [e]
                 (timbre/error e "Failed to update API mode")
                 {:status 500
                  :body {:message "Could not update API mode."}})}
   (let [conn (get @state/db :connection)
         api-status (:api-status conn)
         api-mode (normalize-api-mode (get-in request [:body :api-mode]))]
     (cond
       (or (nil? conn) (empty? conn) (nil? (:url conn)))
       {:status 422 :body {:message "Not connected. Sign in first."}}

       (nil? api-status)
       {:status 422 :body {:message "No API availability status. Disconnect and sign in again."}}

       (not (api-mode-allowed? api-status api-mode))
       {:status 422
        :body {:message (str "Selected API mode '" (name api-mode)
                             "' is not available with the current credentials or host.")
               :api-status api-status
               :api-mode api-mode
               :availability (:availability api-status)}}

       :else
       (do
         (swap! state/db assoc-in [:connection :api-mode] api-mode)
         {:status 200
          :body {:api-mode api-mode
                 :availability (:availability api-status)
                 :api-status api-status}})))))

;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'ring.middleware.resource)
;(debug/debug-ns *ns*)
