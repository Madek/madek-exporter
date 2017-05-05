(ns madek.app.front.core
  (:require
    [madek.app.front.routes]
    [madek.app.front.state :as state]
    [reagent.core :as reagent :refer [atom]]
    [secretary.core :as secretary :include-macros true :refer [defroute]]
    ))

(defn root-component []
  [:div
   [:div.navigation
    [:h3 "Madek Navigation"]
    [:ul.nav
     [:li [:a {:href "/about"} "About"]]
     [:li [:a {:href "/debug"} "Debug"]]]]
   (when-let [page @state/current-page]
     [:div.page [page]])])

(defn mount-root []
  (reagent/render [root-component]
                  (.getElementById js/document "app")))

(defn init! []
  (madek.app.front.routes/init)
  (mount-root)
  )
