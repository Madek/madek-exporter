(ns madek.app.front.download.step2
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
      req {:title (i18n/t :download/back-step1-req)})))

(defn submit []
  (let [req {:method :patch
             :json-params (assoc
                            (select-keys
                              @form-data*
                              [:prefix_meta_key :recursive :skip_media_files])
                            :step2-completed true)
             :path "/download"}]
    (request/send-off
      req {:title (i18n/t :download/step2-req)})))

;;; recursive ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn recursive-component []
  (when (= :collection (-> @download* :entity :type))
    [:div.recursive
     [:h4 (i18n/t :download/recursion)]
     [:div.checkbox
      [:label
       [:input {:type :checkbox
                :on-click #(set-value :recursive (-> @form-data* :recursive not))
                :checked (-> @form-data* :recursive)}]
       (i18n/t :download/recurse)]
      [:p.help-block (i18n/t :download/recursion-help)]]]))

;;; skip files ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn skip-media-files-component []
  [:div.skip-files
   [:h4 (i18n/t :download/skip-files)]
   [:div.checkbox
    [:label
     [:input {:type :checkbox
              :on-click #(set-value :skip_media_files (-> @form-data* :skip_media_files not))
              :checked (-> @form-data* :skip_media_files)}]
     (i18n/t :download/skip-files-label)]
    [:p.help-block (i18n/t :download/skip-files-help)]]])


;;; prefix meta-key ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn load-vocabulary-meta-keys []
  (when-let [vocabulary @vocabulary*]
    (when-not (-> @form-data* (get vocabulary nil))
      (request/send-off
        {:method :get
         :path (str "/vocabularies/" vocabulary "/meta-keys/")}
        {:title (i18n/t :download/fetch-metakeys {:vocabulary vocabulary})}
        :callback (fn [resp]
                    (when (:success resp)
                      (set-value vocabulary
                                 (conj (->> (:body resp)
                                            (filter #(= (:meta_datum_object_type %)
                                                        "MetaDatum::Text"))
                                            (map #(assoc % :key (:id %)))
                                            (sort-by :label)
                                            (into []))
                                       {:label ""
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

(defn- meta-key-option-label [option]
  (if (or (nil? (:id option)) (= "" (:id option)))
    (i18n/t :download/no-meta-key-prefix)
    (:label option)))

(defn prefix-meta-key-component []
  (when-let [meta-keys-options (-> @form-data* (get @vocabulary* nil))]
    [:div.form-group.meta-key
     [:label (i18n/t :download/meta-key)]
     [:select.form-control
      {:on-change #(set-value :prefix_meta_key (.. % -target -value))
       :value (-> @form-data* :prefix_meta_key)}
      (for [option meta-keys-options]
        [:option
         {:key (:id option)
          :value (:id option)}
         (meta-key-option-label option)])]
     [:p.help-block
      (i18n/t :download/meta-key-help-1-before)
      [:code (i18n/t :download/no-meta-key-prefix)]
      (i18n/t :download/meta-key-help-1-after)]
     [:p.help-block
      (i18n/t :download/meta-key-help-2)]]))


;;; prefix vocabulary ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn load-vocabularies []
  (when-not (-> @form-data* :vocabularies)
    (request/send-off
      {:method :get
       :path "/vocabularies/"}
      {:title (i18n/t :download/fetch-vocabularies)}
      :callback (fn [req] (set-value :vocabularies
                                     (->> (:body req)
                                          (map #(assoc % :key (:id %)))
                                          (sort-by :label)))))))

(defn vocabulary-form-group-component []
  [:div.form-group.vocabulary
   [:label (i18n/t :download/vocabulary)]
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
   [:h4 (i18n/t :download/prefix)]
   [:p (i18n/t :download/prefix-help)]
   [prefix-vocabulary-component]
   [prefix-meta-key-component]
   ])


;;; form ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn form-component []
  [:div.form
   [recursive-component]
   [prefix-component]
   [skip-media-files-component]
   [:div.form-actions
    [:div.pull-left
     [:button.btn.btn-info
      {:on-click back}
      (i18n/t :download/back-step1) ]]
    [:div.pull-right
     [:button.btn.btn-primary
      {:on-click submit}
      (i18n/t :download/continue-step3) ]]
    [:div.clearfix]]])

(defn debug-component []
  (when (:debug @state/client-db)
    [:div.debug
     [:h3 (i18n/t :debug/title)]
     [:section.data
      [:h4 "form-data*"]
      [:pre (with-out-str (pprint @form-data*))]]]))

(defn main-component []
  [:div.download-form
   [:h2 (i18n/t :download/step2-title)]
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
