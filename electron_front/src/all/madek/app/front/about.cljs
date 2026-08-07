(ns madek.app.front.about
  (:require
    [fipp.edn :refer [pprint]]
    [madek.app.front.i18n :as i18n]
    [madek.app.front.state :as state]
    [madek.app.front.env]
    [madek.app.front.release :as release]
    [cljs.nodejs :as nodejs]
    ))

(defn version-component []
  [:div.version
   [:h2 (i18n/t :about/version-release)]
   [:p (i18n/t :about/version-prefix) [:code @release/version*]]
   [release/release-info-component]])

(defn electron-component []
  [:div.electron
   [:h2 (i18n/t :about/electron-components)]
   [:ul
    [:li
     [:span (i18n/t :about/nodejs-version)]
     [:span.code (-> @state/electron-main-db
                     :environment :nodejs-version)]]
    [:li
     [:span (i18n/t :about/electron-version)]
     [:span.code (-> nodejs/process .-versions .-electron)]]
    [:li
     [:span (i18n/t :about/electron-modules)]
     [:ul
      (doall
        (for [[k v] (js->clj (.-versions nodejs/process))]
          [:li {:key (str k)} (str k ": " v)]))]]
    [:li
     [:span (i18n/t :about/chrome-version)]
     [:span.code (.-appVersion js/navigator)]]]])

(defn page []
  [:div.about
   [:h1 (i18n/t :about/title)]
   [version-component]
   [electron-component]
   ])
