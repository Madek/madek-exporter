(ns madek.app.front.main
  (:require
    [madek.app.front.routes :as routes]
    [madek.app.front.state :as state]
    [reagent.core :as reagent :refer [atom]]
    [secretary.core :as secretary :include-macros true :refer [defroute]]
    ))

(defn naviagation []
  [:div.navigation
   [:h3 "Madek Navigation"]
   [:ul.nav
    [:li [:a {:href (routes/about-page)} "About"]]
    [:li [:a {:href (routes/connection-page)} "Connection"]]
    [:li [:a {:href (routes/debug-page)} "Debug"]]
    ]])


(defn root-component []
  [:div
   [naviagation]
   (when-let [page @state/current-page]
     [:div.page [page]])])

(defn mount-root []
  (reagent/render [root-component]
                  (.getElementById js/document "app")))

(defn init! []
  (madek.app.front.routes/init)
  (mount-root)
  (state/init)
  )
