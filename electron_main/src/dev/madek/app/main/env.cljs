(ns madek.app.main.env
  (:require
    [cljs.nodejs :as nodejs]
    [cljs-uuid-utils.core :as uuid]
    ))

(def path (nodejs/require "path"))
(def fs (nodejs/require "fs"))

(def env :dev)

(def app-dir
  (let [process-dir (.realpathSync fs ".")
        relative-app-dir "./app/dev"]
    (.resolve path process-dir relative-app-dir)))

(def jvm-port 8383)
(def jvm-password "secret")


