(ns madek.app.front.download.step2
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

(def form-data* (reaction (-> @state/client-db :download :download-form)))

(def set-value
  (form-utils/create-update-form-data-setter
    state/client-db
    [:download :download-form]))

(defn back []
  (let [req {:method :patch
             :json-params {:step1-completed false}
             :path "/download"}]
    (request/send-off
      req {:title "Delete Pre-Checked Export Entity!"})))

(defn submit []
  (let [req {:method :patch
             :json-params (assoc @form-data*
                                 :step2-completed true)
             :path "/download"}]
    (request/send-off
      req {:title "Export/Download Step-2"})))

(defn summary-component []
  [:div.summary
   [:p " Export/Download the set "
    [:a {:href "#"
         :on-click #(.openExternal
                      shell (-> @state/jvm-main-db :download :entity :url))}
     [:em (-> @state/jvm-main-db :download :entity :title)]]]
   [:p "Export to " [:code (-> @state/jvm-main-db :download :target-directory)] "."]])

(defn form-component []
  [:div.form
   [:div.form-group
    [:label "Recursive export: "]
    [:br]
    [:input {:type :checkbox
             :on-click #(set-value :recursive (-> @form-data* :recursive not))
             :checked (-> @form-data* :recursive)} ] " recurse"
    [:p.help-block "Sets and media-entries  which are descendants of the selected set "
     " will be exported  if this option is enabled.."
     "Recurring entities will be replaced by symbolic links the file system and "
     "therefore infinite recursion is avoided." ]]
   [:div.pull-left
    [:button.btn.btn-warning
     {:on-click back}
     "Back" ]]
   [:div.pull-right
    [:button.btn.btn-primary
     {:on-click submit}
     "Next" ]]
   [:div.clearfix]])

(defn debug-component []
  (when (:debug @state/client-db)
    [:div.debug
     [:h3 "Debug Step-2"]
     [:section.data
      [:h4 "form-data*"]
      [:pre (with-out-str (pprint @form-data*))]]]))

(defn main-component []
  [:div.download-form
   [:h2 "Step 2 - Set Advanced Options" ]
   [summary-component]
   [form-component]
   [debug-component]
   ])

(defn component []
  (reagent/create-class
    {:component-did-mount (fn [])
     :render main-component }))


