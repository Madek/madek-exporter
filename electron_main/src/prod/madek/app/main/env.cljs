(ns madek.app.main.env
  (:require
    [cljs.nodejs :as nodejs]
    [cljs-uuid-utils.core :as uuid]
    ))

(def path (nodejs/require "path"))
(def fs (nodejs/require "fs"))

(def env :prod)

(def ^:private js-dir (js* "__dirname"))

;; Packaged: …/Resources/app.asar/js → resources = …/Resources
;; Repo prod: …/app/prod/js → resources = …/app (JAR may sit next to app-dir instead).
(def resources-dir
  (.resolve path js-dir ".." ".."))

(def ^:private asar-app-dir
  (.resolve path resources-dir "app.asar"))

(def ^:private nested-app-dir
  (.resolve path resources-dir "app"))

(def ^:private sibling-app-dir
  (.resolve path js-dir ".."))

(defn- package-json? [dir]
  (.existsSync fs (.resolve path dir "package.json")))

(def app-dir
  (cond
    (.existsSync fs asar-app-dir) asar-app-dir
    (package-json? nested-app-dir) nested-app-dir
    (package-json? sibling-app-dir) sibling-app-dir
    :else nested-app-dir))

(defn- e2e? []
  (= "1" (some-> js/process .-env (aget "MADEK_E2E"))))

(def jvm-port
  (let [from-env (some-> js/process .-env (aget "MADEK_JVM_PORT") js/parseInt)]
    (if (and (e2e?) from-env (not (js/isNaN from-env)))
      from-env
      (+ 1024 (rand-int (- 65535 1024))))))

(def jvm-password
  (or (when (e2e?)
        (some-> js/process .-env (aget "MADEK_JVM_PASSWORD") not-empty))
      (uuid/make-random-uuid)))
