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
    [logbug.thrown :as thrown]
    ))


(defn auth-info [api-entry-point api-http-opts]
  (let [response (-> (roa/get-root api-entry-point :default-conn-opts api-http-opts)
                     (roa/relation :auth-info)
                     (roa/get {}))]
    (logging/debug (-> response roa/data))
    response))

(defn connect-to-madek-server [request]
  (catcher/snatch
    {:return-fn (fn [e] {:status 500 :body (thrown/stringify e)})}
    (logging/debug 'connect {:request request})
    (let [connect-body (:body request)
          url (-> connect-body :madek-url)
          http-options (utils/options-to-http-options connect-body)
          auth-info-response (-> (roa/get-root (str url "/api/")
                                               :default-conn-opts http-options)
                                 (roa/relation :auth-info)
                                 (roa/get {}))]
      (if-not auth-info-response
        {:status 422 :body {:message (str "no auth-info response, "
                                          "check your connection parameters.")}}
        (let [response-status (:status auth-info-response)]
          (logging/debug 'auth-info-response auth-info-response)
          (logging/debug 'response-status response-status)
          (if-not (and (>= response-status 200) (< response-status 300))
            {:status response-status :body {:message "authentication failed"}}
            (let [auth-info (roa/data auth-info-response)]
              (swap! state/db
                     (fn [db conn-params]
                       (assoc-in db [:connection] conn-params))
                     (merge
                       (select-keys connect-body [:session-token :madek-url])
                       (select-keys auth-info [:login :session-expiration-seconds])
                       {:session-expiration-ref-time (str (time/now))}))
              {:status 202})))))))


