(ns madek.app.main.env
  (:require
    [cljs.nodejs :as nodejs]
    ))

(def path (nodejs/require "path"))
(def fs (nodejs/require "fs"))

(def env :prod)

(def app-dir
  (let [dirname (js* "__dirname")
        relative-app-dir ".."]
    (.resolve path dirname relative-app-dir)))

(def jvm-port (+ 1024 (rand-int (- 65535 1024))))
