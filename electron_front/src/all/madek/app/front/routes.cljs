(ns madek.app.front.routes
  (:require
    [madek.app.front.about]
    [madek.app.front.debug]
    [madek.app.front.state]

    [accountant.core :as accountant]
    [secretary.core :as secretary :include-macros true :refer [defroute]]
    ))

;(defroute main-page "/index.html" [] (reset! madek.app.front.state/current-page nil))

;(defroute "/" [] (reset! madek.app.front.state/current-page nil))

(defroute debug-page "/debug" []
  (reset! madek.app.front.state/current-page madek.app.front.debug/page))

(defroute about-page "/about" []
  (reset! madek.app.front.state/current-page madek.app.front.about/page))

(defn init []
  (accountant/configure-navigation!
    {:nav-handler (fn [path] (secretary/dispatch! path))
     :path-exists? (fn [path] (secretary/locate-route path))})
  ;(secretary/dispatch! "/about")
  ;(accountant/dispatch-current!)
  )
