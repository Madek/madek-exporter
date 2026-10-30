(ns madek.app.main.env
  (:require
    [cljs.nodejs :as nodejs]
    [cljs-uuid-utils.core :as uuid]
    ))

(def path (nodejs/require "path"))
(def fs (nodejs/require "fs"))

(def env :dev)

(def resources-dir
  nil)

(def asar-enabled?
  false)

(def app-dir
  (let [process-dir (.realpathSync fs ".")
        relative-app-dir "./app/dev"]
    (.resolve path process-dir relative-app-dir)))

(def jvm-port
  (let [p (some-> js/process .-env (aget "MADEK_JVM_PORT") js/parseInt)]
    (if (and p (not (js/isNaN p))) p 8383)))
(def jvm-password "secret")


