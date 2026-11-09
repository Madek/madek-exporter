(ns madek.app.front.about
  (:require
    [clojure.string :as string]
    [madek.app.front.bridge :as bridge]
    [madek.app.front.i18n :as i18n]
    [madek.app.front.release :as release]
    [madek.app.front.state :as state]
    ))

(def section-id "help-about")

;; Former Version & OS toc id — keep as deep-link alias.
(def legacy-version-section-id "help-version")

(defn- full-version []
  (when-let [rel (-> @state/electron-main-db :environment :latest-release)]
    (str (:version_major rel)
         "." (:version_minor rel)
         "." (:version_patch rel)
         (when-let [pre (:version_pre rel)] (str "-" pre))
         (when-let [build (:version_build rel)] (str "+" build)))))

(defn- external-link
  ([url] (external-link url (string/replace url #"^https://" "")))
  ([url label]
   [:a {:href url
        :on-click (fn [e]
                    (.preventDefault e)
                    (bridge/open-external url))}
    label]))

(defn version-component []
  (let [version (or (full-version) @release/version*)
        url (when version
              (str "https://github.com/Madek/madek-exporter/releases#release-" version))]
    [:div.version
     [:h5 (i18n/t :about/version-release)]
     [:p (i18n/t :about/version-prefix) [:code version]]
     [:p (i18n/t :help/date-of-creation) [:code "2026-08-07"]]
     (when url
       [:p (i18n/t :help/release) [external-link url]])
     [release/release-info-component]]))

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

(defn supported-os-component []
  [:div.supported-os
   [:h5 (i18n/t :help/supported-os)]
   [:ul
    [:li "macOS (x64, ARM64)"]
    [:li "Linux (x64, ARM64)"]
    [:li "Windows (x64)"]]])

(defn section []
  [:div.about {:id section-id}
   [:h4 (i18n/t :about/title)]
   [version-component]
   [electron-component]
   [supported-os-component]])
