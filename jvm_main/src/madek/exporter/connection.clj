(ns madek.exporter.connection
  (:require
   [clj-time.core :as time]
   [clj-time.format :as time-format]
   [json-roa.client.core :as roa]
   [logbug.catcher :as catcher]
   [logbug.debug :as debug]
   [logbug.thrown :as thrown]
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
   {:return-fn (fn [e] {:status 500 :body (thrown/stringify e)})}
   (try (let [connect-body (:body request)
              url (some-> connect-body :url utils/presence)
              login (some-> connect-body :login utils/presence)
              password (some-> connect-body :password utils/presence)
              _ (debug 'connect-request
                       {:path (:uri request)
                        :has-body (map? connect-body)
                        :has-url (boolean url)
                        :has-login (boolean login)
                        :has-password (boolean password)})
              _ (when-not (map? connect-body)
                  (throw (ex-info "invalid connect payload" {:status 422 :message "Expected JSON object body"})))
              _ (when-not url
                  (throw (ex-info "missing url" {:status 422 :message "Missing required field: url"})))
              _ (when (not= (boolean login) (boolean password))
                  (throw (ex-info "invalid credentials" {:status 422
                                                          :message "Provide both login and password, or neither."})))
              http-options (utils/options-to-http-options connect-body)
              api-root (roa/get-root (str url "/api/")
                                     :default-conn-opts http-options)
              auth-info (when (:basic-auth http-options)
                          (-> api-root (roa/relation :auth-info) (roa/get {})))]
          (debug 'http-options http-options)
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
                          {:http-options http-options
                           :auth-info auth-info}
                          (select-keys auth-info [:login :email_address])))
                  {:status 202})))))
        (catch Exception e
          (cond
            (= (-> e ex-data :status) 401) {:status 401
                                            :body "Authentication failed. Check your credentials!"}
            (= (-> e ex-data :status) 422) {:status 422
                                            :body {:message (or (-> e ex-data :message)
                                                                "Invalid connect request")}}
            :else (throw e))))))

(defn disconnect [_]
  (swap! state/db assoc-in [:connection] {}))

;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'ring.middleware.resource)
;(debug/debug-ns *ns*)
