(ns madek.app.server.utils
  (:refer-clojure :exclude [str keyword])
  (:require
    [json-roa.client.core :as roa]
    [clojure.string :as string]
    [clojure.java.shell :refer [sh]]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    [logbug.thrown :as thrown]
    )
  (:import
    [org.apache.commons.lang3 SystemUtils]
    [java.awt Desktop]
    [java.net URI]
    ))

(defn deep-merge [& vals]
  (if (every? map? vals)
    (apply merge-with deep-merge vals)
    (last vals)))

(defn exit [status & msgs]
  (println (string/join \newline msgs))
  (System/exit status))

(defn os-browse [s]
  (.browse (Desktop/getDesktop) (URI. s)))

(defn presence [v]
  "Returns nil if v is a blank string or if v is an empty collection.
   Returns v otherwise."
  (cond
    (string? v) (if (clojure.string/blank? v) nil v)
    (coll? v) (if (empty? v) nil v)
    :else v))

(defn options-to-http-options [options]
  (let [{login :login password :password
         session-token :session-token} options]
    (if (and (presence login)
             (presence password))
      {:basic-auth [login password]}
      {:basic-auth [password]})))

(defn str
  "Like clojure.core/str but maps keywords to strings without preceding colon."
  ([] "")
  ([x]
   (if (keyword? x)
     (subs (clojure.core/str x) 1)
     (clojure.core/str x)))
  ([x & yx]
   (apply clojure.core/str  (concat [(str x)] (apply str yx)))))

(defn keyword
  "Like clojure.core/keyword but coerces an unknown single argument x
  with (-> x cider-ci.utils.core/str cider-ci.utils.core/keyword).
  In contrast clojure.core/keyword will return nil for anything
  not being a String, Symbol or a Keyword already (including
  java.util.UUID, Integer)."
  ([name] (cond (keyword? name) name
                :else (clojure.core/keyword (str name))))
  ([ns name] (clojure.core/keyword ns name)))

;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'ring.middleware.resource)
;(debug/debug-ns *ns*)
