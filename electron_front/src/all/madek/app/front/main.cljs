(ns madek.app.front.main
  (:refer-clojure :exclude [str keyword send-off])
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    )
  (:require
    [madek.app.front.connection :as connection]
    [madek.app.front.release]
    [madek.app.front.request :as request]
    [madek.app.front.state :as state]
    [madek.app.front.utils :refer [str keyword deep-merge]]

    [accountant.core :as accountant]
    [cljs.nodejs :as nodejs]
    [reagent.core :as reagent :refer [atom]]
    [secretary.core :as secretary :include-macros true :refer [defroute]]
    ))

(def Electron (nodejs/require "electron"))

(def shell (.-shell Electron))

(def github-repo-url "https://github.com/Madek/madek-exporter")

(defn- nav-link [path label & [attrs]]
  [:a (merge {:href path
              :on-click (fn [e]
                          (.preventDefault e)
                          (accountant/navigate! path))}
             attrs)
   label])

(defn- nav-item [path label]
  [:li {:class (when (= @state/current-path path) "active")}
   [nav-link path label]])

(defn- external-link [url label & [attrs]]
  [:a (merge {:href url
              :on-click (fn [e]
                          (.preventDefault e)
                          (.openExternal shell url))}
             attrs)
   label])

(defn naviagation []
  [:nav.navbar.navbar-inverse
   [:div.container-fluid
    [:div.navbar-header
     [external-link github-repo-url "Madek-Exporter" {:class "navbar-brand"}]]
    [:ul.navbar-nav.nav
     [nav-item "/connection" "Connection"]
     [nav-item "/download" "Export"]
     [nav-item "/debug" "Debug"]
     [nav-item "/help" "Help"]]
    [:ul.nav.navbar-nav.navbar-right
     [:li
      [:a [connection/compact-component]]]]]])

(defn root-component []
  [:div
   [request/modal]
   [naviagation]
   [:div.container-fluid
    [madek.app.front.release/update-available-alert-component]
    (when-let [page @state/current-page]
      [:div.page [page]])]])
(defn mount-root []
  (reagent/render [root-component]
                  (.getElementById js/document "app")))

(defn init! []
  (madek.app.front.routes/init)
  (mount-root)
  (state/init))
