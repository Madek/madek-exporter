(ns madek.app.front.init
  (:require
    [figwheel.client :as fw :include-macros true]
    [madek.app.front.core :as core]
    ))

(enable-console-print!)

(defn start-madek []
  (core/init!))

(fw/watch-and-reload
  :websocket-url   "ws://localhost:3449/figwheel-ws"
  :jsload-callback 'start-madek)

(start-madek)
