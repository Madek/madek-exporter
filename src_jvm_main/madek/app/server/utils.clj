(ns madek.app.server.utils
  (:require
    [madek.app.server.state :as state]

    [json-roa.client.core :as roa]
    [clojure.string :as string]
    [clojure.java.shell :refer [sh]]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.thrown :as thrown]
    )
  (:import
    [org.apache.commons.lang3 SystemUtils]
    [java.awt Desktop]
    [java.net URI]
    ))


(defn options-to-http-options [options]
  (let [{login :login password :password
         session-token :session-token} options]
    (cond-> {}
      (and login password) (assoc :basic-auth
                                  [login password])
      session-token (assoc :cookies
                           {"madek-session"
                            {:value session-token}}))))

(defn deep-merge [& vals]
  (if (every? map? vals)
    (apply merge-with deep-merge vals)
    (last vals)))


(defn exit [status & msgs]
  (println (string/join \newline msgs))
  (System/exit status))

(defn os-browse [s]
  (.browse (Desktop/getDesktop) (URI. s)))
