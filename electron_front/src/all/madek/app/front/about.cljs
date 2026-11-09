(ns madek.app.front.about
  (:require
    [fipp.edn :refer [pprint]]
    [madek.app.front.i18n :as i18n]
    [madek.app.front.state :as state]
    [madek.app.front.env]
    [madek.app.front.release :as release]
    [madek.app.front.bridge :as bridge]
    ))

(defn version-component []
  [:div.version
   [:h2 (i18n/t :about/version-release)]
   [:p (i18n/t :about/version-prefix) [:code @release/version*]]
   [release/release-info-component]])

(defn electron-component []
  (let [v (bridge/versions)]
    [:div.electron
     [:h2 (i18n/t :about/electron-components)]
     [:ul
      [:li
       [:span (i18n/t :about/nodejs-version)]
       [:span.code (or (-> @state/electron-main-db
                           :environment :nodejs-version)
                       (:node v))]]
      [:li
       [:span (i18n/t :about/electron-version)]
       [:span.code (:electron v)]]
      [:li
       [:span (i18n/t :about/electron-modules)]
       [:ul
        (doall
          (for [[k val] (:versions v)]
            [:li {:key (str k)} (str k ": " val)]))]]
      [:li
       [:span (i18n/t :about/chrome-version)]
       [:span.code (or (:chrome v) (.-appVersion js/navigator))]]]]))

(defn page []
  [:div.about
   [:h1 (i18n/t :about/title)]
   [version-component]
   [electron-component]
   ])
