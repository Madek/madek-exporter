(ns madek.app.front.debug
  (:require
    [fipp.edn :refer [pprint]]
    [reagent.core :as reagent]
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
     " Show per page debug info"]]

   [:p "Electron main "
    (if (empty? @state/electron-main-db)
      "waiting ..."
      "connected! ")]
   [:p "JVM main "
    (if (:jvm-main-options  @state/jvm-main-db)
      "connected! "
      "waiting ...")]
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
       "Clear logs"]]
     (if (seq logs)
       [:pre
        (with-out-str
          (pprint (reverse logs)))]
       [:div.alert.alert-info
        [:p "No app logs yet."]])]))

(defn content []
  [:div
   [:ul.nav.nav-tabs {:style {:margin-bottom "1em"}}
    [:li {:class (when (= @active-tab* :state) "active")}
     [:a {:href "#"
          :on-click (fn [e]
                      (.preventDefault e)
                      (reset! active-tab* :state))}
      "State"]]
    [:li {:class (when (= @active-tab* :logs) "active")}
     [:a {:href "#"
          :on-click (fn [e]
                      (.preventDefault e)
                      (reset! active-tab* :logs))}
      "App logs"]]]
   (case @active-tab*
     :logs [logs-content]
     [state-content])])

(defn page []
  [:div.debug
   [:h3 "Debug"]
   [content]
   ])
