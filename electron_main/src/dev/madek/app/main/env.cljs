(ns madek.app.main.env
  (:require
    [cljs.nodejs :as nodejs]
    ))

(def path (nodejs/require "path"))
(def fs (nodejs/require "fs"))

(def env :dev)

(def app-dir
  (let [process-dir (.realpathSync fs ".")
        relative-app-dir "./app/dev"]
    (.resolve path process-dir relative-app-dir)))
