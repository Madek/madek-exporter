(ns madek.exporter.utils
  (:refer-clojure :exclude [str keyword])
  (:require
   [clj-http.conn-mgr :as conn-mgr]
   [clojure.java.shell :refer [sh]]
   [clojure.string :as string]
   [json-roa.client.core :as roa]
   [logbug.catcher :as catcher]
   [logbug.debug :as debug]
   [logbug.thrown :as thrown])
  (:import
   [java.awt Desktop]
   [java.net URI]
   [org.apache.commons.lang3 SystemUtils]))

;; Sized for parallel media-entry downloads (concurrency 4).
(defn- make-http-conn-manager []
  (conn-mgr/make-reusable-conn-manager
   {:timeout 5
    :threads 8
    :default-per-route 4}))

(defonce ^:private http-conn-manager
  (atom (make-http-conn-manager)))

(defn reset-http-conn-manager!
  "Shut down the current shared pool and install a fresh one so leaked
  streams from an aborted download cannot starve the next run."
  []
  (let [old @http-conn-manager
        fresh (make-http-conn-manager)]
    (reset! http-conn-manager fresh)
    (when old
      (try
        (conn-mgr/shutdown-manager old)
        (catch Exception _)))))

;; Fail loudly instead of waiting forever for a saturated pool / dead peer.
(def ^:private default-http-timeouts
  {:connection-timeout 15000
   :connection-request-timeout 30000})

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
  "Build serializable auth options only (no live connection-manager)."
  (letfn [(basic-auth-header [login password]
            (let [raw (clojure.core/str login ":" password)
                  encoded (.encodeToString (java.util.Base64/getEncoder)
                                           (.getBytes raw "UTF-8"))]
              (clojure.core/str "Basic " encoded)))]
    (let [login (some-> options :login presence)
          password (some-> options :password presence)
          api-token (some-> options :api-token presence)]
      (cond
        (and login password)
        {:basic-auth [login password]
         :headers {"authorization" (basic-auth-header login password)}}

        api-token
        {:headers {"authorization" (clojure.core/str "token " api-token)}}

        :else
        {}))))

(defn with-http-pool [http-options]
  "Attach the shared connection pool for live HTTP calls. Do not store the
  result in state/db — the manager is not serializable over Sente."
  (-> (or http-options {})
      (merge default-http-timeouts)
      (assoc :connection-manager @http-conn-manager)))

(defn authenticated-http-options? [http-options]
  (boolean (or (:basic-auth http-options)
               (some-> http-options :headers (clojure.core/get "authorization") presence))))

(defn str
  "Like clojure.core/str but maps keywords to strings without preceding colon."
  ([] "")
  ([x]
   (if (keyword? x)
     (subs (clojure.core/str x) 1)
     (clojure.core/str x)))
  ([x & yx]
   (apply clojure.core/str (concat [(str x)] (apply str yx)))))

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
