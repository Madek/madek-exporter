(ns madek.app.front.main
  (:refer-clojure :exclude [str keyword send-off])
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    )
  (:require
    [madek.app.front.connection :as connection]
    [madek.app.front.i18n :as i18n]
    [madek.app.front.release :as release]
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
(def zhdk-url "https://zhdk.medienarchiv.ch/")

(def burger-menu-open* (atom false))
(def language-menu-open* (atom false))

(defn- close-burger-menu []
  (reset! burger-menu-open* false))

(defn- close-language-menu []
  (reset! language-menu-open* false))

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

(defn- export-nav-item []
  (if @connection/connected?*
    [nav-item "/download" (i18n/t :nav/export)]
    [:li.disabled
     [:a {:href "#"
          :on-click (fn [e] (.preventDefault e))}
      (i18n/t :nav/export)]]))

(defn- external-link [url label & [attrs]]
  [:a (merge {:href url
              :on-click (fn [e]
                          (.preventDefault e)
                          (.openExternal shell url))}
             attrs)
   label])

(defn- zhdk-logo []
  [:svg.zhdk-logo
   {:xmlns "http://www.w3.org/2000/svg"
    :viewBox "0 0 75 32"
    :role "img"
    :aria-label "ZHdK"}
   [:path {:d "M15.3 31.3h7.1v.7h-7.1zm0-4.9h7.1v.7h-7.1zm0-26.4h7.1v.7h-7.1zm6.5 17.7L29.6 9V5.2H15.5v4.2h7.3L15 17.9V22h15v-4.3z"
           :fill "#fff"}]])

(defn- language-menu []
  (let [open? @language-menu-open*
        locale @i18n/locale*]
    [:li.dropdown.language-menu {:class (when open? "open")}
     [:a.dropdown-toggle
      {:href "#"
       :role "button"
       :aria-haspopup "true"
       :aria-expanded (if open? "true" "false")
       :title (i18n/t :lang/title)
       :on-click (fn [e]
                   (.preventDefault e)
                   (close-burger-menu)
                   (swap! language-menu-open* not))}
      [:span.glyphicon.glyphicon-globe {:aria-hidden "true"}]
      [:span.language-code (get i18n/LOCALE-LABELS locale)]
      [:span.sr-only (i18n/t :lang/title)]]
     [:ul.dropdown-menu.dropdown-menu-right
      (doall
        (for [loc i18n/LOCALES]
          ^{:key (name loc)}
          [:li {:class (when (= loc locale) "active")}
           [:a {:href "#"
                :on-click (fn [e]
                            (.preventDefault e)
                            (i18n/set-locale! loc)
                            (close-language-menu))}
            (get i18n/LOCALE-LABELS loc)]]))]]))

(defn- burger-menu []
  (let [open? @burger-menu-open*
        connected? @connection/connected?*]
    [:li.dropdown.burger-menu {:class (when open? "open")}
     [:a.dropdown-toggle
      {:href "#"
       :role "button"
       :aria-haspopup "true"
       :aria-expanded (if open? "true" "false")
       :title (i18n/t :nav/menu)
       :on-click (fn [e]
                   (.preventDefault e)
                   (close-language-menu)
                   (swap! burger-menu-open* not))}
      [:span.glyphicon.glyphicon-menu-hamburger {:aria-hidden "true"}]
      [:span.sr-only (i18n/t :nav/menu)]]
     [:ul.dropdown-menu.dropdown-menu-right
      (when connected?
        (list
          (when-let [entity @connection/connected-entity*]
            [:li.dropdown-header.burger-session {:key "session-user"} entity])
          (when-let [target @connection/connected-target*]
            [:li.dropdown-header.burger-session {:key "session-url"} target])))
      [:li.dropdown-header
       (i18n/t :nav/version {:version (or @release/version* "—")})]
      [:li
       [:a {:href "/help"
            :on-click (fn [e]
                        (.preventDefault e)
                        (close-burger-menu)
                        (accountant/navigate! "/help"))}
        (i18n/t :nav/contact)]]
      (when connected?
        [:li
         [:a {:href "#"
              :on-click (fn [e]
                          (.preventDefault e)
                          (close-burger-menu)
                          (connection/disconnect))}
          (i18n/t :nav/logout)]])]]))

(defn naviagation []
  [:nav.navbar.navbar-inverse
   [:div.container-fluid
    [:div.navbar-header
     [external-link zhdk-url
      [zhdk-logo]
      {:class "navbar-brand navbar-brand-logo"}]
     [external-link github-repo-url "Madek-Exporter"
      {:class "navbar-brand"}]]
    [:ul.navbar-nav.nav
     [connection/status-icon-component]
     [nav-item "/connection" (i18n/t :nav/connection)]
     [export-nav-item]
     [nav-item "/debug" (i18n/t :nav/debug)]
     [nav-item "/help" (i18n/t :nav/help)]]
    [:ul.nav.navbar-nav.navbar-right
     [language-menu]
     [burger-menu]]]])

(defn root-component []
  [:div
   [request/modal]
   [naviagation]
   [:div.container-fluid
    [release/update-available-alert-component]
    (when-let [page @state/current-page]
      [:div.page [page]])]])
(defn mount-root []
  (reagent/render [root-component]
                  (.getElementById js/document "app")))

(defn init! []
  (i18n/init!)
  (madek.app.front.routes/init)
  (mount-root)
  (state/init))
