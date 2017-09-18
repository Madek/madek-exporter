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

(def download* (reaction (-> @state/jvm-main-db :download)))

(def vocabulary* (reaction
                   (-> @form-data* :vocabulary presence)))

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
             :json-params (assoc (select-keys @form-data*
                                              [:prefix_meta_key :recursive :skip_media_files :download_meta_data_schema])
                                 :step2-completed true)
             :path "/download"}]
    (request/send-off
      req {:title "Export/Download Step-2"})))

;;; recursive ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn recursive-component []
  (when (= :collection (-> @download* :entity :type))
    [:div.recursive
     [:h4 "Recursion on sets"]
    [:div.form-group
     [:input {:type :checkbox
              :on-click #(set-value :recursive (-> @form-data* :recursive not))
              :checked (-> @form-data* :recursive)} ] " recurse"
     [:p.help-block "Sets and media-entries  which are descendants of the selected set "
      " will be exported  if this option is enabled."
      "Recurring entities will be replaced by symbolic links the file system and "
      "therefore infinite recursion is avoided." ]]]))

;;; skip files ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn skip-media-files-component []
  [:div.skip-files
   [:h4 "Skip files"]
   [:div.form-group
    [:input {:type :checkbox
             :on-click #(set-value :skip_media_files (-> @form-data* :skip_media_files not))
             :checked (-> @form-data* :skip_media_files)} ] " skip files"
    [:p.help-block "The download of any media-files will be skipped if this is checked. "
     "This means that only the meta-data of media-entries or sets will be downloaded. " ]]])

;;; download meta-data schema ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn download-meta-data-schema-component []
  [:div.meta-data-schema
   [:h4 "Meta-Data Schema"]
   [:div.form-group
    [:input {:type :checkbox
             :on-click #(set-value :download_meta_data_schema (-> @form-data* :download_meta_data_schema not))
             :checked (-> @form-data* :download_meta_data_schema)} ] " download meta-data schema"
    [:p.help-block "If this is checked the file `meta-data_schema.json` will be created in your download directory."]]])


;;; prefix meta-key ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn load-vocabulary-meta-keys []
  (when-let [vocabulary @vocabulary*]
    (when-not (-> @form-data* (get vocabulary nil))
      (request/send-off
        {:method :get
         :path (str "/vocabularies/" vocabulary "/meta-keys/")}
        {:title (str "Fetch MetaKeys for " vocabulary)}
        :callback (fn [resp]
                    (when (:success resp)
                      (set-value vocabulary
                                 (conj (->> (:body resp)
                                            (filter #(= (:meta_datum_object_type %)
                                                        "MetaDatum::Text"))
                                            (map #(assoc % :key (:id %)))
                                            (sort-by :label)
                                            (into []))
                                       {:label "NO META-KEY PREFIX"
                                        :id ""
                                        :key ""
                                        :vocabulary_id vocabulary
                                        :meta_datum_object_type "MetaDatum::Text"
                                        }))))))))

(add-watch vocabulary* :lazy-load-meta-keys-watch
           (fn [_ _ _ vocabulary]
             (when vocabulary
               (when-not (get @form-data* vocabulary nil)
                 (load-vocabulary-meta-keys)))))

(defn prefix-meta-key-component []
  (when-let [meta-keys-options (-> @form-data* (get @vocabulary* nil))]
    [:div.form-group.meta-key
     [:label "Meta-key:"]
     [:select.form-control
      {:on-change #(set-value :prefix_meta_key (.. % -target -value))
       :value (-> @form-data* :prefix_meta_key)}
      (for [option meta-keys-options]
        [:option
         {:key (:id option)
          :value (:id option)}
         (:label option)])]
     [:p.help-block
      "If the " [:code "NO META-KEY PREFIX"]
      " option is select neither prefix nor underscore will be present. "
      "This is probably a good choice for automatized post processing. "]
     [:p.help-block
      "If the value of the corresponding meta-key is blank or if there is no "
      " such meta-key present for the entity "
      " the id will be still prefixed with an underscore."]]))


;;; prefix vocabulary ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn load-vocabularies []
  (when-not (-> @form-data* :vocabularies)
    (request/send-off
      {:method :get
       :path "/vocabularies/"}
      {:title "Fetch Vocabularies"}
      :callback (fn [req] (set-value :vocabularies
                                     (->> (:body req)
                                          (map #(assoc % :key (:id %)))
                                          (sort-by :label)))))))

(defn vocabulary-form-group-component []
  [:div.form-group.vocabulary
   [:label "Vocabulary:"]
   [:select.form-control
    {:on-change #(let [voc (.. % -target -value)]
                   (when-not (= voc (-> @form-data* :vocabulary))
                     (set-value :prefix_meta_key ""))
                   (set-value :vocabulary voc))
     :value (-> @form-data* :vocabulary)}
    (for [option (or (-> @form-data* :vocabularies) {})]
      [:option
       {:key (:id option)
        :value (:id option)
        :data-id (:id option)
        :data-label (:label option)}
       (:label option)])]])

(defn prefix-vocabulary-component []
  (reagent/create-class
    {:component-did-mount load-vocabularies
     :render vocabulary-form-group-component}))


;;; prefix ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn prefix-component []
  [:div.prefix
   [:h4 "Prefix"]
   [:p "For every set or media-entry a folder will be created. "
    "The name of the folder consists of a prefix, an underscore and the id of the entity."
    "The prefix will be determined by the meta-key given in this section."]
   [prefix-vocabulary-component]
   [prefix-meta-key-component]
   ])


;;; form ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn form-component []
  [:div.form
   [recursive-component]
   [prefix-component]
   [skip-media-files-component]
   [download-meta-data-schema-component]
   [:div.pull-left
    [:button.btn.btn-info
     {:on-click back}
     "Back to step 1" ]]
   [:div.pull-right
    [:button.btn.btn-primary
     {:on-click submit}
     "Continue to step 3" ]]
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
   [form-component]
   [debug-component]])

(defn initialize-form-data []
  (when-not (:vocabulary @form-data*)
    (set-value :vocabulary "madek_core"))
  (when-not (:prefix_meta_key @form-data*)
    (set-value :prefix_meta_key "madek_core:title")))

(defn component []
  (reagent/create-class
    {;:component-will-mount #(swap! state/client-db assoc-in [:download :download-form] {})
     :component-did-mount initialize-form-data
     :render main-component }))


