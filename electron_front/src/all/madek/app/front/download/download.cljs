(ns madek.app.front.download.download
  (:refer-clojure :exclude [str keyword])
  (:require
    [madek.app.front.utils :refer [str keyword deep-merge presence]]
    [madek.app.front.state :as state]
    [madek.app.front.request :as request]
    [madek.app.front.connection :as connection]

    [inflections.core :refer [pluralize]]

    [reagent.ratom :as ratom :refer [reaction]]
    ))

(def download* (reaction (-> @state/jvm-main-db :download)))

(defn errors-component []
  [:div.errors
   (when (->@download* :errors empty? not)
     [:h3 "Errors"]
     (doall (for [[ek ev] (->@download* :errors)]
              [:div.panel.panel-danger
               [:div.panel-heading
                [:h3.panel-title ek]]
               [:div.panel-body
                [:pre.wrap ev]]])))])

(defn progress-component []
  [:div.progress
   [:div.progress-bar
    {:class (cond
              (-> @download* :download-finished not) "progress-bar-info active"
              (-> @download* :errors empty?) "progress-bar-success"
              :else "progress-bar-danger")
     :aria-valuenow "50"
     :aria-valuemin "0"
     :aria-valuemax "100"
     :style {:width "100%"}}
    (if (-> @download* :download-finished not)
      " Downloading: "
      " Downloaded: ")
    (pluralize
      (->> @download* :items (map second) (filter #(= "Collection" (:type %))) count)
      "Set") ", "
    (pluralize
      (->> @download* :items (map second) (filter #(= "MediaEntry" (:type %))) count)
      "MediaEntry")]])

(defn downloading-component []
  [:div
   [:h2 "Downloading / Exporting Now!"]
   [progress-component] ])

(defn clear-export-steps []
  (let [req {:method :patch
             :json-params
             {:step1-completed false
              :step2-completed false
              :download-started false
              :download-finished false
              :items nil
              :errors nil}
             :path "/download"}]
    (request/send-off
      req {:title "Dismiss Download!"})))

(defn disconnect []
  (clear-export-steps)
  (connection/disconnect)
  (swap! state/client-db assoc-in [:connection :form :password] nil))

(defn dismiss-component []
  [:div.dismiss
   [:div.form.pull-left
    [:button.btn.btn-primary
     {:on-click clear-export-steps}
     "Back to step 1 - start a new export" ]]
   [:div.form.pull-right
    [:button.btn.btn-warning
     {:on-click disconnect}
     "Disconnect" ]]])

(defn downloaded-component []
  [:div
   [:h2 "Download / Export Finished"]
   [progress-component]
   [errors-component]
   [dismiss-component]])


