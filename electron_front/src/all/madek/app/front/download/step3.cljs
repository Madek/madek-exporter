(ns madek.app.front.download.step3
  (:refer-clojure :exclude [str keyword])
  (:require
    [madek.app.front.utils :refer [str keyword deep-merge presence]]
    [madek.app.front.utils.form :as form-utils]
    [madek.app.front.i18n :as i18n]
    [madek.app.front.download.download :refer [entity-export-line-component]]
    [madek.app.front.request :as request]

    [reagent.core :as reagent]
    [fipp.edn :refer [pprint]]
    [reagent.ratom :as ratom :refer [reaction]]
    [madek.app.front.state :as state]
    [madek.app.front.env]
    ))

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
   [entity-export-line-component]
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
  [:div.form-actions.action-buttons
   [:button.btn.btn-link
    {:on-click back}
    (i18n/t :download/back-step2) ]
   [:button.btn.btn-primary
    {:on-click submit}
    (i18n/t :download/start-export) ]])

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
