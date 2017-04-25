(ns madek-front.core
  (:require  [reagent.core :as reagent :refer [atom]]))

(defonce state (atom {:message "Hello Madek User!"}))

(defn root-component []
  [:div
   [:p "Application loaded!"]
   [:p (:message @state)]])

(defn mount-root [setting]
  (reagent/render [root-component]
                  (.getElementById js/document "app")))

(defn init! [setting]
  (mount-root setting))
