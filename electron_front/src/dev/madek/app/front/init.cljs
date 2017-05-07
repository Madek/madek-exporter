(ns madek.app.front.init
  (:require
    [figwheel.client :as fw :include-macros true]
    [madek.app.front.main :as main]
    ))

(enable-console-print!)

(defn start-madek []
  (main/init!))

(fw/watch-and-reload
  :websocket-url   "ws://localhost:8384/figwheel-ws"
  :jsload-callback 'start-madek)

(start-madek)
