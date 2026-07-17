(ns madek.app.main.env
  (:require
    [cljs.nodejs :as nodejs]
    [cljs-uuid-utils.core :as uuid]
    ))

(def path (nodejs/require "path"))
(def fs (nodejs/require "fs"))

(def env :prod)

(def resources-dir
  (.resolve path (js* "__dirname") ".." ".."))

(def asar-app-dir
  (.resolve path resources-dir "app.asar"))

(def unpacked-app-dir
  (.resolve path resources-dir "app"))

(def asar-enabled?
  (.existsSync fs asar-app-dir))

(def app-dir
  (if asar-enabled? asar-app-dir unpacked-app-dir))

(def jvm-port (+ 1024 (rand-int (- 65535 1024))))

(def jvm-password (uuid/make-random-uuid))
