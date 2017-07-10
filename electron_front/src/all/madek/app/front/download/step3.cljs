(ns madek.app.front.download.step3
  (:refer-clojure :exclude [str keyword])
  (:require
    [madek.app.front.utils :refer [str keyword deep-merge presence]]
    [madek.app.front.utils.form :as form-utils]
    [madek.app.front.request :as request]

    [reagent.core :as reagent]
    [fipp.edn :refer [pprint]]
    [reagent.ratom :as ratom :refer [reaction]]
    [madek.app.front.state :as state]
    [madek.app.front.env]
    [cljs.nodejs :as nodejs]
    [inflections.core :as inflections]
    )
  (:import
    [goog Uri]
    ))


(def Electron (nodejs/require "electron"))

(def shell (.-shell Electron))

(defn back []
  (let [req {:method :patch
             :json-params {:step2-completed false}
             :path "/download"}]
    (request/send-off
      req {:title "Back to :download-entity-checked"})))

(defn submit []
  (let [req {:method :post
             :json-params nil
             :path "/download"}]
    (request/send-off
      req {:title "Start Export/Download!"})))

(defn summary-component []
  [:div.summary
   [:p " Export/Download the " (case (-> @state/jvm-main-db :download :entity :type)
                                 :collection "set"
                                 :media-entry "media-entry"
                                 "???") " "
    [:a {:href "#"
         :on-click #(.openExternal
                      shell (-> @state/jvm-main-db :download :entity :url))}
     [:em (-> @state/jvm-main-db :download :entity :title)]]]
   [:p "Export to " [:code (-> @state/jvm-main-db :download :target-directory)] "."]
   [:p "Recursive: " [:code (-> @state/jvm-main-db :download :recursive not not str)] "."]
   [:p "Meta-key used for prefixing entities: " (if-let [pmk (-> @state/jvm-main-db :download :prefix_meta_key presence)]
                                                  [:code pmk]
                                                  [:span "none"]) "."]
   [:p "Skip media-files " [:code (-> @state/jvm-main-db :download :skip_media_files not not str)]"."]])

(defn debug-component []
  (when (:debug @state/client-db)
    [:div.debug
     [:h3 "Debug"]
     ]))

(defn form-component []
  [:div.form
   [:div.pull-left
    [:button.btn.btn-info
     {:on-click back}
     "Back to step 2" ]]
   [:div.pull-right
    [:button.btn.btn-primary
     {:on-click submit}
     "Start Download/Export!" ]]
   [:div.clearfix]])

(defn main-component []
  [:div.download-form
   [:h2 "Step 3 - Review and Start Export" ]
   [summary-component]
   [form-component]
   [debug-component]
   ])

(defn component []
  (reagent/create-class
    {:component-did-mount (fn [])
     :render main-component }))


