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

(def new-export-structure "new-export-structure")
(def legacy-export-structure "legacy-export-structure")

(defn export-structure-value []
  (or (-> @state/jvm-main-db :download :export_structure presence)
      new-export-structure))

(defn set-export-structure [value]
  (let [req {:method :patch
             :json-params {:export_structure value}
             :path "/download"}]
    (request/send-off
      req {:title (i18n/t :download/export-structure-req)})))

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

(defn export-structure-label [value]
  (case value
    "legacy-export-structure" (i18n/t :download/export-structure-legacy)
    (i18n/t :download/export-structure-new)))

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
   [:p (i18n/t :download/skip-media-files) [:code (-> @state/jvm-main-db :download :skip_media_files not not str)]"."]
   [:p (i18n/t :download/export-structure-label)
    [:code (export-structure-label (export-structure-value))] "."]])

(defn export-structure-component []
  (let [current (export-structure-value)]
    [:div.export-structure
     [:h4 (i18n/t :download/export-structure)]
     [:div.form-group
      [:label
       [:input {:type :radio
                :name "export_structure"
                :value new-export-structure
                :checked (= current new-export-structure)
                :on-change #(set-export-structure new-export-structure)}]
       " " (i18n/t :download/export-structure-new)]
      [:p.help-block (i18n/t :download/export-structure-new-help)]]
     [:div.form-group
      [:label
       [:input {:type :radio
                :name "export_structure"
                :value legacy-export-structure
                :checked (= current legacy-export-structure)
                :on-change #(set-export-structure legacy-export-structure)}]
       " " (i18n/t :download/export-structure-legacy)]
      [:p.help-block (i18n/t :download/export-structure-legacy-help)]]]))

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
   [export-structure-component]
   [form-component]
   [debug-component]
   ])

(defn ensure-default-export-structure []
  (when-not (-> @state/jvm-main-db :download :export_structure presence)
    (set-export-structure new-export-structure)))

(defn component []
  (reagent/create-class
    {:component-did-mount (fn [] (ensure-default-export-structure))
     :render main-component }))
