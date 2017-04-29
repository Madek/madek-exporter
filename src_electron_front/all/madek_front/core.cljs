(ns madek-front.core
  (:require
    [reagent.core :as reagent :refer [atom]]
    [madek-front.state :as state]
    [fipp.edn :refer [pprint]]
    ))

(defonce state (atom {:message "Hello Madek User!"}))

(defn root-component []
  [:div
   [:p "Application loaded!"]
   [:p (:message @state)]
   [:div.app-db
    [:h3 "Application DB"]
    [:pre
     (with-out-str (pprint @state/db))]]
   [:div.app-db
    [:h3 "Client DB"]
    [:pre
     (with-out-str (pprint @state/client-db))]]])

(defn mount-root [setting]
  (reagent/render [root-component]
                  (.getElementById js/document "app")))

(defn init! [setting]
  (mount-root setting))
