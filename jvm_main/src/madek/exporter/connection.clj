(ns madek.exporter.connection
  (:require
   [clj-http.client :as http]
   [clojure.data.json :as json]
   [clojure.string :as string]
   [json-roa.client.core :as roa]
   [logbug.catcher :as catcher]
   [logbug.debug :as debug]
   [madek.exporter.state :as state]
   [madek.exporter.utils :as utils]
   [taoensso.timbre :as timbre :refer [info debug]]))

(defn auth-info [api-entry-point api-http-opts]
  (let [response (-> (roa/get-root api-entry-point :default-conn-opts api-http-opts)
                     (roa/relation :auth-info)
                     (roa/get {}))]
    (debug (-> response roa/data))
    response))

(defn- token-auth? [http-options]
  (some-> http-options :headers (get "authorization")
          (#(and % (string/starts-with? (string/lower-case %) "token ")))))

(defn basic-auth-only? [http-options]
  (boolean (and (:basic-auth http-options) (not (token-auth? http-options)))))

(defn- status->reason [status]
  (cond
    (nil? status) :unreachable
    (= 404 status) :not-found
    (and status (>= status 400)) :auth-failed
    :else nil))

(defn- json-body [body]
  (when body
    (try
      (json/read-str body :key-fn keyword)
      (catch Exception _ nil))))

(defn- probe-result [available status auth-info reason error]
  (cond-> {:available available}
    status (assoc :status status)
    auth-info (assoc :auth-info auth-info)
    reason (assoc :reason reason)
    error (assoc :error error)))

(defn probe-api-v1 [url http-options]
  (try
    (if-not (utils/authenticated-http-options? http-options)
      (probe-result false 401 nil :auth-failed "Missing credentials")
      (let [live (utils/with-http-pool http-options)
            response (-> (roa/get-root (str url "/api/") :default-conn-opts live)
                         (roa/relation :auth-info)
                         (roa/get {}))]
        (if-not response
          (probe-result false nil nil :unreachable "no auth-info response")
          (let [response-status (:status response)]
            (if (and (>= response-status 200) (< response-status 300))
              (probe-result true response-status (roa/data response) nil nil)
              (probe-result false response-status nil
                            (status->reason response-status)
                            "Authentication failed"))))))
    (catch Exception e
      (let [{:keys [status message]} (ex-data e)]
        (probe-result false (or status nil) nil
                      (or (status->reason status) :unreachable)
                      (or message (.getMessage e)))))))

(defn probe-api-v2 [url http-options]
  (if (basic-auth-only? http-options)
    (probe-result false nil nil :unsupported-auth
                  "API v2 requires token or session authentication (not HTTP Basic).")
    (try
      (let [response (http/get (str url "/api-v2/auth-info/")
                               (merge (utils/with-http-pool http-options)
                                      {:accept "application/json"
                                       :throw-exceptions false}))
            status (:status response)]
        (if (and (>= status 200) (< status 300))
          (probe-result true status (json-body (:body response)) nil nil)
          (probe-result false status nil
                        (status->reason status)
                        (or (:message (json-body (:body response)))
                            "Authentication failed"))))
      (catch Exception e
        (let [{:keys [status message]} (ex-data e)]
          (probe-result false (or status nil) nil
                        (or (status->reason status) :unreachable)
                        (or message (.getMessage e))))))))

(defn compute-availability [api api-v2]
  (cond
    (and (:available api) (:available api-v2)) :both
    (:available api) :api
    (:available api-v2) :api-v2
    :else :neither))

(defn probe-apis [url http-options]
  (let [api (probe-api-v1 url http-options)
        api-v2 (probe-api-v2 url http-options)]
    {:api api
     :api-v2 api-v2
     :availability (compute-availability api api-v2)}))

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
              api-status (probe-apis url http-options)
              v1 (:api api-status)]
          (debug 'http-options
                 (cond-> http-options
                   (get-in http-options [:headers "authorization"])
                   (assoc-in [:headers "authorization"] "***redacted***")
                   (:basic-auth http-options)
                   (assoc :basic-auth "***redacted***")))
          (debug 'api-status api-status)
          (if-not (:available v1)
            (let [status (cond
                           (= (:reason v1) :auth-failed) 401
                           (some? (:status v1)) (:status v1)
                           :else 422)
                  message (if (= status 401)
                            "Authentication failed. Check your credentials."
                            (or (:error v1)
                                (str "no auth-info response, "
                                     "check your connection parameters.")))]
              {:status status :body {:message message :api-status api-status}})
            (let [auth-info (:auth-info v1)]
              (swap! state/db
                     (fn [db conn-params]
                       (assoc-in db [:connection] conn-params))
                     (merge
                      (select-keys connect-body [:url])
                      {:http-options http-options
                       :auth-info auth-info
                       :api-status api-status}
                      (select-keys auth-info [:login :email_address])))
              {:status 202 :body {:api-status api-status}})))
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
