(ns madek.app.front.routes
  (:require
    [madek.app.front.about]
    [madek.app.front.connection]
    [madek.app.front.debug]
    [madek.app.front.download]
    [madek.app.front.help]
    [madek.app.front.state]

    [accountant.core :as accountant]
    [secretary.core :as secretary :include-macros true :refer [defroute]]
    ))

(defroute about-page "/about" []
  (reset! madek.app.front.state/current-page madek.app.front.about/page)
  (reset! madek.app.front.state/current-path "/about"))

(defroute connection-page "/connection" []
  (reset! madek.app.front.state/current-page madek.app.front.connection/page)
  (reset! madek.app.front.state/current-path "/connection"))

(defroute download-page "/download" []
  (reset! madek.app.front.state/current-page madek.app.front.download/page)
  (reset! madek.app.front.state/current-path "/download"))

(defroute debug-page "/debug" []
  (reset! madek.app.front.state/current-page madek.app.front.debug/page)
  (reset! madek.app.front.state/current-path "/debug"))

(defroute help-page "/help" []
  (reset! madek.app.front.state/current-page madek.app.front.help/page)
  (reset! madek.app.front.state/current-path "/help"))


; under windows the paths somehow come es e.g. "/C:/about" instead
; of "/about"; this might not cover all cases but is also dangerous
; to just remove everything before the colon
(defn fix-path [path]
  (clojure.string/replace path #"^/\w:" ""))

(defn init []
  (accountant/configure-navigation!
    {:nav-handler (fn [path]
                    (let [path (fix-path path)]
                      (when (secretary/locate-route path)
                        (secretary/dispatch! path))))
     :path-exists? (fn [path] (-> path fix-path secretary/locate-route))})
  (accountant/navigate! "/connection"))
