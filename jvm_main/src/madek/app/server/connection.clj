(ns madek.app.server.connection
  (:require
    [madek.app.server.utils :as utils]
    [madek.app.server.state :as state]
    [json-roa.client.core :as roa]

    [clj-time.core :as time]
    [clj-time.format :as time-format]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    [logbug.thrown :as thrown]
    ))


(defn auth-info [api-entry-point api-http-opts]
  (let [response (-> (roa/get-root api-entry-point :default-conn-opts api-http-opts)
                     (roa/relation :auth-info)
                     (roa/get {}))]
    (logging/debug (-> response roa/data))
    response))

(defn connect-with-authentication []
  )

(defn connect-anonymously []
  )

(defn connect-to-madek-server [request]
  (catcher/snatch
    {:return-fn (fn [e] {:status 500 :body (thrown/stringify e)})}
    (logging/debug 'connect {:request request})
    (try (let [connect-body (:body request)
               url (-> connect-body :url)
               http-options (utils/options-to-http-options connect-body)
               api-root (roa/get-root (str url "/api/")
                                      :default-conn-opts http-options)
               auth-info (when (:basic-auth http-options)
                           (-> api-root (roa/relation :auth-info) (roa/get {})))]
           (logging/debug 'http-options http-options)
           (logging/debug 'api-root api-root)
           (logging/debug 'auth-info auth-info)
           (if-not auth-info
             {:status 422 :body {:message (str "no auth-info response, "
                                               "check your connection parameters.")}}
             (let [response-status (:status auth-info)]
               (logging/debug 'auth-info auth-info)
               (logging/debug 'response-status response-status)
               (if-not (and (>= response-status 200) (< response-status 300))
                 {:status response-status :body {:message "Authentication failed"}}
                 (let [auth-info (roa/data auth-info)]
                   (swap! state/db
                          (fn [db conn-params]
                            (assoc-in db [:connection] conn-params))
                          (merge
                            (select-keys connect-body [:url])
                            {:http-options http-options}
                            (select-keys auth-info [:login :email_address])))
                   {:status 202})))))
         (catch Exception e
           (cond
             (= (-> e ex-data :status) 401) {:status 401
                                             :body "Authentication failed. Check your credentials!"}
             :else (throw e))))))



;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'ring.middleware.resource)
(debug/debug-ns *ns*)
