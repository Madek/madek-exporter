(ns madek.app.front.download.step3
  (:refer-clojure :exclude [str keyword])
  (:require
    [madek.app.front.utils :refer [str keyword deep-merge presence]]
    [madek.app.front.utils.form :as form-utils]
    [madek.app.front.i18n :as i18n]
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
      req {:title (i18n/t :download/back-step2-req)})))

(defn submit []
  (let [req {:method :post
             :json-params nil
             :path "/download"}]
    (request/send-off
      req {:title (i18n/t :download/start-export-req)})))

(defn summary-component []
  [:div.summary
   [:p (i18n/t :download/export-the)
    (case (-> @state/jvm-main-db :download :entity :type)
      :collection (i18n/t :download/entity-set)
      :media-entry (i18n/t :download/entity-media-entry)
      (i18n/t :download/entity-unknown)) " "
    [:a {:href "#"
         :on-click #(.openExternal
                      shell (-> @state/jvm-main-db :download :entity :url))}
     [:em (-> @state/jvm-main-db :download :entity :title)]]]
   [:p (i18n/t :download/export-to) [:code (-> @state/jvm-main-db :download :target-directory)] "."]
   [:p (i18n/t :download/recursive-label) [:code (-> @state/jvm-main-db :download :recursive not not str)] "."]
   [:p (i18n/t :download/meta-key-prefixing) (if-let [pmk (-> @state/jvm-main-db :download :prefix_meta_key presence)]
                                                  [:code pmk]
                                                  [:span (i18n/t :download/none)]) "."]
   [:p (i18n/t :download/skip-media-files) [:code (-> @state/jvm-main-db :download :skip_media_files not not str)]"."]])

(defn debug-component []
  (when (:debug @state/client-db)
    [:div.debug
     [:h3 (i18n/t :debug/title)]
     ]))

(defn form-component []
  [:div.form
   [:div.pull-left
    [:button.btn.btn-info
     {:on-click back}
     (i18n/t :download/back-step2) ]]
   [:div.pull-right
    [:button.btn.btn-primary
     {:on-click submit}
     (i18n/t :download/start-export) ]]
   [:div.clearfix]])

(defn main-component []
  [:div.download-form
   [:h2 (i18n/t :download/step3-title)]
   [summary-component]
   [form-component]
   [debug-component]
   ])

(defn component []
  (reagent/create-class
    {:component-did-mount (fn [])
     :render main-component }))
