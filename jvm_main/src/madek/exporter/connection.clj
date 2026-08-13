(ns madek.exporter.connection
  (:require
   [clj-time.core :as time]
   [clj-time.format :as time-format]
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
              api-root (roa/get-root (str url "/api/")
                                     :default-conn-opts live-http-options)
              auth-info (when (utils/authenticated-http-options? http-options)
                          (-> api-root (roa/relation :auth-info) (roa/get {})))]
          (debug 'http-options
                 (cond-> http-options
                   (get-in http-options [:headers "authorization"])
                   (assoc-in [:headers "authorization"] "***redacted***")
                   (:basic-auth http-options)
                   (assoc :basic-auth "***redacted***")))
          (debug 'api-root api-root)
          (debug 'auth-info auth-info)
          (if-not auth-info
            {:status 422 :body {:message (str "no auth-info response, "
                                              "check your connection parameters.")}}
            (let [response-status (:status auth-info)]
              (debug 'auth-info auth-info)
              (debug 'response-status response-status)
              (if-not (and (>= response-status 200) (< response-status 300))
                {:status response-status :body {:message "Authentication failed"}}
                (let [auth-info (roa/data auth-info)]
                  (swap! state/db
                         (fn [db conn-params]
                           (assoc-in db [:connection] conn-params))
                         (merge
                          (select-keys connect-body [:url])
                          ;; Store auth-only options (no connection-manager).
                          {:http-options http-options
                           :auth-info auth-info}
                          (select-keys auth-info [:login :email_address])))
                  {:status 202})))))
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
