(ns madek.app.front.help
  (:require
    [clojure.string :as string]
    [madek.app.front.breadcrumb :as breadcrumb]
    [madek.app.front.i18n :as i18n]
    [madek.app.front.release :as release]
    [madek.app.front.state :as state]
    [cljs.nodejs :as nodejs]
    ))

(def Electron (nodejs/require "electron"))

(def shell (.-shell Electron))

(defn- external-link
  ([url] (external-link url (string/replace url #"^https://" "")))
  ([url label]
   [:a {:href url
        :on-click (fn [e]
                    (.preventDefault e)
                    (.openExternal shell url))}
    label]))

(defn full-version []
  (when-let [rel (-> @state/electron-main-db :environment :latest-release)]
    (str (:version_major rel)
         "." (:version_minor rel)
         "." (:version_patch rel)
         (when-let [pre (:version_pre rel)] (str "-" pre))
         (when-let [build (:version_build rel)] (str "+" build)))))

(defn version-component []
  (let [version (or (full-version) @release/version*)
        url (when version
              (str "https://github.com/Madek/madek-exporter/releases#release-" version))]
    [:div.version
     [:h4 (i18n/t :help/version)]
     [:p (i18n/t :help/version-prefix) [:code version]]
     [:p (i18n/t :help/date-of-creation) [:code "2026-08-07"]]
     (when url
       [:p (i18n/t :help/release) [external-link url]])]))

(defn supported-os-component []
  [:div.supported-os
   [:h4 (i18n/t :help/supported-os)]
   [:ul
    [:li "macOS (x64, ARM64)"]
    [:li "Linux (x64, ARM64)"]
    [:li "Windows (x64)"]]])

(defn login-variants-component []
  [:div.login-variants
   [:h4 (i18n/t :help/login-variants)]
   [:h5 (i18n/t :help/sign-in-token)]
   [:ul
    [:li [:code "api-token"] " only"]]
   [:h5 (i18n/t :help/sign-in-login)]
   [:ul
    [:li [:code "login/api-token"]]
    [:li [:code "email/api-token"]]
    [:li [:code "api-client/pw"]]
    [:li [:code "login/pw"] (i18n/t :help/login-underscore) [:code "_"] (i18n/t :help/login-underscore-end)]]
   [:p (i18n/t :help/login-help)]])

(defn references-component []
  [:div.references
   [:h4 (i18n/t :help/references)]
   [:ul
    [:li [external-link "https://zhdk.medienarchiv.ch/"]]
    [:li [external-link "https://wiki.zhdk.ch/medienarchiv/doku.php?id=madek-exporter"]]
    [:li [external-link "https://zhdk.medienarchiv.ch/api/browser/"]]
    [:li [external-link "https://github.com/Madek/madek-exporter/releases"]]]])

(defn kontakt-component []
  [:div.kontakt
   [:h4 [external-link "https://www.zhdk.ch/miz/archive-1387/madek/kontakt-1874"
                        (i18n/t :help/contact)]]
   [:p (i18n/t :help/email) [external-link "mailto:support.medienarchiv@zhdk.ch"
                                           "support.medienarchiv@zhdk.ch"]]])

(defn page []
  [:div.page.help
   [breadcrumb/page-breadcrumb (i18n/t :help/title)]
   [version-component]
   [supported-os-component]
   [login-variants-component]
   [references-component]
   [kontakt-component]])
