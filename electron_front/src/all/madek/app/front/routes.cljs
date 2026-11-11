(ns madek.app.front.routes
  (:require
    [accountant.core :as accountant]
    [madek.app.front.about]
    [madek.app.front.connection]
    [madek.app.front.debug]
    [madek.app.front.download]
    [madek.app.front.help]
    [madek.app.front.state]

    [secretary.core :as secretary :include-macros true :refer [defroute]]
    ))

(defroute about-page "/about" []
  ;; About lives under Help; keep /about as a deep-link.
  (reset! madek.app.front.help/scroll-to* madek.app.front.about/section-id)
  (reset! madek.app.front.state/current-page madek.app.front.help/page)
  (reset! madek.app.front.state/current-path "/help"))

(defroute connection-page "/connection" []
  (reset! madek.app.front.state/current-page madek.app.front.connection/page)
  (reset! madek.app.front.state/current-path "/connection"))

(defroute download-page "/download" []
  (if (madek.app.front.connection/can-access-download?)
    (do
      (reset! madek.app.front.state/current-page madek.app.front.download/page)
      (reset! madek.app.front.state/current-path "/download"))
    (accountant/navigate! "/connection")))

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
