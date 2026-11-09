(ns madek.app.front.main
  (:refer-clojure :exclude [str keyword send-off])
  (:require
    [madek.app.front.connection :as connection]
    [madek.app.front.i18n :as i18n]
    [madek.app.front.release :as release]
    [madek.app.front.request :as request]
    [madek.app.front.routes :as routes]
    [madek.app.front.state :as state]
    [madek.app.front.utils :refer [str keyword deep-merge]]

    [madek.app.front.bridge :as bridge]
    [madek.app.front.env :as env]

    [accountant.core :as accountant]
    [reagent.core :as reagent :refer [atom]]
    [secretary.core :as secretary :include-macros true :refer [defroute]]
    ))

(def github-repo-url "https://github.com/Madek/madek-exporter")
(def zhdk-url "https://zhdk.medienarchiv.ch/")

(def burger-menu-open* (atom false))
(def language-menu-open* (atom false))

(defn- close-burger-menu []
  (reset! burger-menu-open* false))

(defn- close-language-menu []
  (reset! language-menu-open* false))

(defn- event-inside? [event selector]
  (boolean
    (when-let [target (.-target event)]
      (when (.-closest target)
        (.closest target selector)))))

(defn- dismiss-menus-on-outside-click [event]
  (when-not (event-inside? event "li.language-menu")
    (close-language-menu))
  (when-not (event-inside? event "li.burger-menu")
    (close-burger-menu)))

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
  (if @connection/export-ready?*
    [nav-item "/download" (i18n/t :nav/export)]
    [:li.disabled
     [:a {:href "#"
          :on-click (fn [e] (.preventDefault e))}
      (i18n/t :nav/export)]]))

(defn- external-link [url label & [attrs]]
  [:a (merge {:href url
              :on-click (fn [e]
                          (.preventDefault e)
                          (bridge/open-external url))}
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

(defn- burger-page [path label disabled?]
  [:li.burger-page {:class (cond
                             disabled? "disabled"
                             (= @state/current-path path) "active")}
   [:a {:href (if disabled? "#" path)
        :on-click (fn [e]
                    (.preventDefault e)
                    (close-burger-menu)
                    (when-not disabled?
                      (accountant/navigate! path)))}
    label]])

(defn- burger-page-items []
  (let [export-ready? @connection/export-ready?*]
    (list
      ^{:key "connection"} [burger-page "/connection" (i18n/t :nav/connection) false]
      ^{:key "export"} [burger-page "/download" (i18n/t :nav/export) (not export-ready?)]
      (when (= env/env :dev)
        ^{:key "debug"} [burger-page "/debug" (i18n/t :nav/debug) false])
      ^{:key "help"} [burger-page "/help" (i18n/t :nav/help) false]
      [:li.divider.burger-page {:key "pages-divider" :role "separator"}])))

(defn- burger-session-item [k label value]
  [:li.dropdown-header.burger-session {:key k}
   [:span.burger-session-label label]
   [:span.burger-session-value {:title value} value]])

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
      (burger-page-items)
      (when connected?
        (list
          (when-let [entity @connection/connected-entity*]
            (burger-session-item "session-user" (i18n/t :nav/signed-in-as) entity))
          (when-let [target @connection/connected-target*]
            (burger-session-item "session-url" (i18n/t :nav/connected-to) target))
          [:li.divider {:key "session-divider" :role "separator"}]))
      [:li.dropdown-header.burger-version
       (i18n/t :nav/version {:version (or @release/version* "—")})]
      [:li.divider {:role "separator"}]
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

(defn- navigation-render []
  [:nav.navbar.navbar-inverse
   [:div.container-fluid.navbar-bar
    [:div.navbar-line.navbar-line-main
     [:div.navbar-header
      [external-link zhdk-url
       [zhdk-logo]
       {:class "navbar-brand navbar-brand-logo"}]
      [external-link github-repo-url "Madek-Exporter"
       {:class "navbar-brand"}]]
     [:ul.nav.navbar-nav.navbar-status
      [connection/status-icon-component]]
     [:ul.nav.navbar-nav.navbar-language
      [language-menu]]
     [:ul.nav.navbar-nav.navbar-burger
      [burger-menu]]]
    [:div.navbar-line.navbar-line-pages
     [:ul.nav.navbar-nav.navbar-pages
      [nav-item "/connection" (i18n/t :nav/connection)]
      [export-nav-item]
      (when (= env/env :dev)
        [nav-item "/debug" (i18n/t :nav/debug)])
      [nav-item "/help" (i18n/t :nav/help)]]]]])

(defn navigation []
  (reagent/create-class
    {:component-did-mount
     (fn [_]
       (.addEventListener js/document "click" dismiss-menus-on-outside-click))
     :component-will-unmount
     (fn [_]
       (.removeEventListener js/document "click" dismiss-menus-on-outside-click))
     :reagent-render navigation-render}))

(defn root-component []
  (let [_ @i18n/locale*]
    [:div
     [request/modal]
     [navigation]
     [:div.container-fluid
      [release/update-available-alert-component]
      (when-let [page @state/current-page]
        [:div.page [page]])]]))
(defn mount-root []
  (reagent/render [root-component]
                  (.getElementById js/document "app")))

(defn init! []
  (i18n/init!)
  (routes/init)
  (mount-root)
  (state/init))
