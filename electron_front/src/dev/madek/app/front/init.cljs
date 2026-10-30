(ns madek.app.front.init
  (:require
    [figwheel.client :as fw :include-macros true]
    [madek.app.front.main :as main]
    ))

(enable-console-print!)

(defn start-madek []
  (main/init!))

;; Mount UI immediately; Figwheel is optional (no figwheel server in E2E/CI).
(start-madek)

(when-not (= (or (some-> js/process .-env (aget "MADEK_E2E")) "") "1")
  (fw/watch-and-reload
    :websocket-url   "ws://localhost:8384/figwheel-ws"
    :jsload-callback 'start-madek))
