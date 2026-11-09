(ns madek.app.front.about
  (:require
    [madek.app.front.i18n :as i18n]
    [madek.app.front.release :as release]
    [madek.app.front.bridge :as bridge]
    ))

(def section-id "help-about")

(defn version-component []
  [:div.version
   [:h5 (i18n/t :about/version-release)]
   [:p (i18n/t :about/version-prefix) [:code @release/version*]]
   [release/release-info-component]])

(defn electron-component []
  (let [v (bridge/versions)]
    [:div.electron
     [:h5 (i18n/t :about/electron-components)]
     [:ul
      [:li
       [:span (i18n/t :about/nodejs-version)]
       [:span.code (:node v)]]
      [:li
       [:span (i18n/t :about/electron-version)]
       [:span.code (:electron v)]]
      [:li
       [:span (i18n/t :about/chrome-version)]
       [:span.code (or (:chrome v) (.-appVersion js/navigator))]]]]))

(defn section []
  [:div.about {:id section-id}
   [:h4 (i18n/t :about/title)]
   [version-component]
   [electron-component]])
