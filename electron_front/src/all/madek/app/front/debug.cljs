(ns madek.app.front.debug
  (:require
    [fipp.edn :refer [pprint]]
    [reagent.core :as reagent]
    [madek.app.front.i18n :as i18n]
    [madek.app.front.state :as state]
    [clojure.walk]
    ))

(defonce active-tab* (reagent/atom :state))

(defn fix-path [path]
  (clojure.string/replace path #"^/\w:" ""))

(defn state-content []
  [:div.content
   [:div.form
    [:div.form-group
     [:input {:type :checkbox :on-click #(swap! state/client-db assoc :debug (-> @state/client-db :debug not))
              :checked (-> @state/client-db :debug)}]
     (i18n/t :debug/show-per-page)]]

   [:p (i18n/t :debug/electron-main)
    (if (empty? @state/electron-main-db)
      (i18n/t :debug/waiting)
      (i18n/t :debug/connected))]
   [:p (i18n/t :debug/jvm-main)
    (if (:jvm-main-options  @state/jvm-main-db)
      (i18n/t :debug/connected)
      (i18n/t :debug/waiting))]
   [:div.jvm-main-db
    [:h3 "Electron-main DB"]
    [:pre
     (with-out-str (pprint @state/electron-main-db))]]
   [:div.jvm-main-db
    [:h3 "JVM-main DB"]
    [:pre
     (with-out-str (pprint @state/jvm-main-db))]]
   [:div.current-page
    [:h3 "Current Page"]
    [:pre
     (with-out-str (pprint @state/current-page))]]
   [:div.app-db
    [:h3 "Client DB"]
    [:pre
     (with-out-str (pprint @state/client-db))]]
   ])

(defn logs-content []
  (let [logs (-> @state/client-db :app-logs)]
    [:div.content
     [:div.form-group
      [:button.btn.btn-default
       {:on-click #(swap! state/client-db assoc :app-logs [])}
       (i18n/t :debug/clear-logs)]]
     (if (seq logs)
       [:pre
        (with-out-str
          (pprint (reverse logs)))]
       [:div.alert.alert-info
        [:p (i18n/t :debug/no-logs)]])]))

(defn content []
  [:div
   [:ul.nav.nav-tabs {:style {:margin-bottom "1em"}}
    [:li {:class (when (= @active-tab* :state) "active")}
     [:a {:href "#"
          :on-click (fn [e]
                      (.preventDefault e)
                      (reset! active-tab* :state))}
      (i18n/t :debug/state)]]
    [:li {:class (when (= @active-tab* :logs) "active")}
     [:a {:href "#"
          :on-click (fn [e]
                      (.preventDefault e)
                      (reset! active-tab* :logs))}
      (i18n/t :debug/app-logs)]]]
   (case @active-tab*
     :logs [logs-content]
     [state-content])])

(defn page []
  [:div.debug
   [:h3 (i18n/t :debug/title)]
   [content]
   ])
