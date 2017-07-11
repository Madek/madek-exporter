(ns madek.app.front.routes
  (:require
    [madek.app.front.about]
    [madek.app.front.connection]
    [madek.app.front.debug]
    [madek.app.front.download]
    [madek.app.front.state]

    [accountant.core :as accountant]
    [secretary.core :as secretary :include-macros true :refer [defroute]]
    ))

(defroute about-page "/about" []
  (reset! madek.app.front.state/current-page madek.app.front.about/page))

(defroute connection-page "/connection" []
  (reset! madek.app.front.state/current-page madek.app.front.connection/page))

(defroute download-page "/download" []
  (reset! madek.app.front.state/current-page madek.app.front.download/page))

(defroute debug-page "/debug" []
  (reset! madek.app.front.state/current-page madek.app.front.debug/page))


; under windows the paths somehow come es e.g. "/C:/about" instead
; of "/about"; this might not cover all cases but is also dangerous
; to just remove everything before the colon
(defn fix-path [path]
  (clojure.string/replace path #"^/\w:" ""))

(defn init []
  (accountant/configure-navigation!
    {:nav-handler (fn [path] (-> path fix-path secretary/dispatch!))
     :path-exists? (fn [path] (-> path fix-path secretary/locate-route))})
  (secretary/dispatch! "/connection")
  (accountant/dispatch-current!))
