(ns madek.app.front.download.download
  (:refer-clojure :exclude [str keyword])
  (:require
    [madek.app.front.utils :refer [str keyword deep-merge presence]]
    [madek.app.front.state :as state]
    [madek.app.front.i18n :as i18n]
    [madek.app.front.request :as request]
    [madek.app.front.connection :as connection]

    [inflections.core :refer [pluralize]]

    [reagent.ratom :as ratom :refer [reaction]]
    ))

(def download* (reaction (-> @state/jvm-main-db :download)))

(defn errors-component []
  [:div.errors
   (when (->@download* :errors empty? not)
     [:h3 (i18n/t :download/errors)]
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
      (i18n/t :download/downloading)
      (i18n/t :download/downloaded))
    (pluralize
      (->> @download* :items (map second) (filter #(= "Collection" (:type %))) count)
      "Set") ", "
    (pluralize
      (->> @download* :items (map second) (filter #(= "MediaEntry" (:type %))) count)
      "MediaEntry")]])

(defn downloading-component []
  [:div
   [:h2 (i18n/t :download/downloading-now)]
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
      req {:title (i18n/t :download/dismiss-req)})))

(defn disconnect []
  (clear-export-steps)
  (connection/disconnect))

(defn dismiss-component []
  [:div.dismiss
   [:div.form.pull-left
    [:button.btn.btn-primary
     {:on-click clear-export-steps}
     (i18n/t :download/back-new-export) ]]
   [:div.form.pull-right
    [:button.btn.btn-warning
     {:on-click disconnect}
     (i18n/t :connection/disconnect) ]]])

(defn downloaded-component []
  [:div
   [:h2 (i18n/t :download/finished)]
   [progress-component]
   [errors-component]
   [dismiss-component]])
