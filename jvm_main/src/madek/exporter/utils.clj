(ns madek.exporter.utils
  (:refer-clojure :exclude [str keyword])
  (:require
   [clj-http.conn-mgr :as conn-mgr]
   [clojure.java.io :as io]
   [clojure.java.shell :refer [sh]]
   [clojure.string :as string]
   [json-roa.client.core :as roa]
   [logbug.catcher :as catcher]
   [logbug.debug :as debug]
   [logbug.thrown :as thrown])
  (:import
   [java.awt Desktop]
   [java.net URI]
   [java.nio.file Paths]
   [org.apache.commons.lang3 SystemUtils]))

;; Sized for parallel media-entry downloads (concurrency 4) and the step-2
;; prefix-options lookups (concurrency 6).
(defn- make-http-conn-manager []
  (conn-mgr/make-reusable-conn-manager
   {:timeout 5
    :threads 8
    :default-per-route 6}))

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

(defn redact-http-options
  "Drop live secrets from maps that may be logged or pushed to clients."
  [http-options]
  (cond-> (or http-options {})
    (get-in http-options [:headers "authorization"])
    (assoc-in [:headers "authorization"] "***redacted***")
    (:basic-auth http-options)
    (assoc :basic-auth "***redacted***")
    (:connection-manager http-options)
    (dissoc :connection-manager)))

(defn redact-connection
  "Public connection snapshot for Sente / Debug (no Madek credentials)."
  [connection]
  (cond-> (dissoc (or connection {}) :http-options)
    (:http-options connection)
    (assoc :authenticated? (authenticated-http-options? (:http-options connection))
           :http-options-present true)))

(defn client-safe-db
  "DB view for Electron clients — never includes Madek auth material."
  [db]
  (cond-> (or db {})
    (contains? db :connection)
    (update :connection redact-connection)))

(def ^:private blocked-export-roots
  #{"/" "/etc" "/usr" "/bin" "/sbin" "/System" "/Library" "/private"
    "C:\\" "C:\\Windows" "C:\\Program Files" "C:\\Program Files (x86)"})

(defn- nio-absolute [path]
  (.normalize (.toAbsolutePath (Paths/get (str path) (into-array String [])))))

(defn under-dir?
  "True when `path` is `dir` or a descendant (after normalize)."
  [dir path]
  (let [dir-p (nio-absolute dir)
        path-p (nio-absolute path)]
    (.startsWith path-p dir-p)))

(defn validate-target-directory
  "Return canonical absolute path or throw ex-info {:type :invalid-target-directory}."
  [path]
  (let [raw (presence (some-> path str))]
    (when-not raw
      (throw (ex-info "Target directory is required."
                      {:type :invalid-target-directory})))
    (let [file (io/file raw)]
      (when-not (.isAbsolute file)
        (throw (ex-info "Target directory must be an absolute path."
                        {:type :invalid-target-directory :path raw})))
      (let [abs (.getCanonicalFile file)
            abs-path (.getPath abs)
            parent (.getParentFile abs)
            home-path (.getPath (.getCanonicalFile
                                 (io/file (System/getProperty "user.home"))))
            tmp-path (.getPath (.getCanonicalFile
                                (io/file (System/getProperty "java.io.tmpdir"))))]
        (when (contains? blocked-export-roots abs-path)
          (throw (ex-info "Refusing to export into a system directory."
                          {:type :invalid-target-directory :path abs-path})))
        (when (.isFile abs)
          (throw (ex-info "Target directory must not be an existing file."
                          {:type :invalid-target-directory :path abs-path})))
        (when-not (or (under-dir? home-path abs-path)
                      (under-dir? tmp-path abs-path)
                      (and parent (.exists parent)))
          (throw (ex-info "Target directory parent does not exist."
                          {:type :invalid-target-directory :path abs-path})))
        abs-path))))

(defn safe-export-filename
  "Basename-only filename safe to join under an export directory."
  [filename fallback-id]
  (let [base (or (last (string/split (-> (str filename)
                                         (string/replace #"\\" "/"))
                                     #"/"))
                 "")
        cleaned (-> base
                    (string/replace #"[^\w.\- ]+" "_")
                    (string/replace #"^\.+" "")
                    string/trim)]
    (if (or (string/blank? cleaned)
            (#{"." ".."} cleaned))
      (str (or (presence fallback-id) "file"))
      cleaned)))

(defn safe-uri-to-open?
  "Allow only http(s) and file: URIs under the current export target."
  [uri target-directory]
  (try
    (let [u (URI. (str uri))
          scheme (some-> (.getScheme u) string/lower-case)]
      (case scheme
        ("http" "https") true
        "file" (and (presence target-directory)
                    (under-dir? target-directory (.getPath u)))
        false))
    (catch Exception _
      false)))

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
