(ns madek.app.front.main
  (:refer-clojure :exclude [str keyword send-off])
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    )
  (:require
    [madek.app.front.connection :as connection]
    [madek.app.front.request :as request]
    [madek.app.front.routes :as routes]
    [madek.app.front.state :as state]
    [madek.app.front.utils :refer [str keyword deep-merge]]
    [reagent.core :as reagent :refer [atom]]
    [secretary.core :as secretary :include-macros true :refer [defroute]]
    ))


(defn naviagation []
  [:nav.navbar.navbar-inverse
   [:div.container-fluid
    [:div.navbar-header
     [:span.navbar-brand {:href "#"} "Madek"]]
    [:ul.navbar-nav.nav
     [:li [:a {:href (routes/about-page)} "About"]]
     [:li [:a {:href (routes/connection-page)} "Connection"]]
     [:li [:a {:href (routes/debug-page)} "Debug"]]
     ]
    [:ul.nav.navbar-nav.navbar-right
     [:li
      [:a [connection/compact-component]]]]]])


(defn root-component []
  [:div.container-fluid
   [request/modal]
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
