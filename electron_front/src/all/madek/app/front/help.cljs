(ns madek.app.front.help
  (:require
    [madek.app.front.release :as release]
    [madek.app.front.state :as state]
    [cljs.nodejs :as nodejs]
    ))

(def Electron (nodejs/require "electron"))

(def shell (.-shell Electron))

(defn- external-link [url]
  [:a {:href url
       :on-click (fn [e]
                   (.preventDefault e)
                   (.openExternal shell url))}
   url])

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
     [:h4 "Version"]
     [:p "Version " [:code version]]
     [:p "Date of creation: " [:code "2026-08-07"]]
     (when url
       [:p "Release: " [external-link url]])]))

(defn supported-os-component []
  [:div.supported-os
   [:h4 "Supported operating systems"]
   [:ul
    [:li "macOS (x64, ARM64)"]
    [:li "Linux (x64, ARM64)"]
    [:li "Windows (x64)"]]])

(defn login-variants-component []
  [:div.login-variants
   [:h4 "Supported login variants"]
   [:h5 "1. Sign in with token"]
   [:ul
    [:li [:code "api-token"] " only"]]
   [:h5 "2. Sign in with login and password"]
   [:ul
    [:li [:code "login/api-token"]]
    [:li [:code "email/api-token"]]
    [:li [:code "api-client/pw"]]
    [:li [:code "login/pw"] " (external user; login must not contain " [:code "_"] ")"]]
   [:p
    "Use the Connection tab “Sign in with login and password”: put the "
    "identity (login, email, or api-client name) in the login field, and the "
    "token or password in the password field."]])

(defn references-component []
  [:div.references
   [:h4 "References"]
   [:ul
    [:li [external-link "https://zhdk.medienarchiv.ch/"]]
    [:li [external-link "https://wiki.zhdk.ch/medienarchiv/doku.php?id=madek-exporter"]]
    [:li [external-link "https://zhdk.medienarchiv.ch/api/browser/"]]
    [:li [external-link "https://github.com/Madek/madek-exporter/releases"]]]])

(defn page []
  [:div.help
   [:h3 "Help"]
   [version-component]
   [supported-os-component]
   [login-variants-component]
   [references-component]])
