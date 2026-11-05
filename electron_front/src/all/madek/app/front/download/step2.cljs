(ns madek.app.front.download.step2
  (:refer-clojure :exclude [str keyword])
  (:require
    [madek.app.front.utils :refer [str keyword deep-merge presence]]
    [madek.app.front.utils.form :as form-utils]
    [madek.app.front.i18n :as i18n]
    [madek.app.front.request :as request]

    [clojure.string]
    [reagent.core :as reagent]
    [fipp.edn :refer [pprint]]
    [reagent.ratom :as ratom :refer [reaction]]
    [madek.app.front.state :as state]
    [madek.app.front.help :as help]
    [madek.app.front.env]
    [accountant.core :as accountant]
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

(defonce loading-vocabularies* (reagent/atom #{}))

(defonce failed-vocabularies* (reagent/atom #{}))

(defn load-meta-keys [vocabulary]
  (when-not (or (-> @form-data* (get vocabulary nil))
                (contains? @loading-vocabularies* vocabulary))
    (swap! loading-vocabularies* conj vocabulary)
    (swap! failed-vocabularies* disj vocabulary)
    (request/send-off
      {:method :get
       :path (str "/vocabularies/" vocabulary "/meta-keys/")}
      {:title (i18n/t :download/fetch-metakeys {:vocabulary vocabulary})}
      :callback (fn [resp]
                  (swap! loading-vocabularies* disj vocabulary)
                  (if (:success resp)
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
                                      }))
                    (swap! failed-vocabularies* conj vocabulary))))))

(add-watch vocabulary* :lazy-load-meta-keys-watch
           (fn [_ _ _ vocabulary]
             (when vocabulary
               (load-meta-keys vocabulary))))

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


;;; available prefixes ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce show-available-prefixes* (reagent/atom false))

(defonce available-prefixes-filter* (reagent/atom ""))

(defonce only-with-value* (reagent/atom false))

;; {:entity-id "…" :status :loading|:loaded|:failed :values {meta-key-id {…}}}
(defonce prefix-values* (reagent/atom nil))

(defn- entity-id []
  (-> @download* :entity :uuid))

(defn- load-prefix-values []
  (let [id (entity-id)]
    (when-not (and (= id (:entity-id @prefix-values*))
                   (#{:loading :loaded} (:status @prefix-values*)))
      (reset! prefix-values* {:entity-id id :status :loading})
      (request/send-off
        {:method :get
         :path "/download/prefix-values"}
        {:title (i18n/t :download/fetch-prefix-values)}
        :callback (fn [resp]
                    (when (= id (:entity-id @prefix-values*))
                      (reset! prefix-values*
                              (if (:success resp)
                                {:entity-id id
                                 :status :loaded
                                 :values (->> (-> resp :body :values)
                                              (map (juxt :meta_key_id identity))
                                              (into {}))}
                                {:entity-id id :status :failed}))))))))

(defn- toggle-available-prefixes []
  (when (swap! show-available-prefixes* not)
    (load-prefix-values)
    (doseq [vocabulary (:vocabularies @form-data*)]
      (load-meta-keys (:id vocabulary)))))

(defn- choose-prefix [vocabulary-id meta-key-id]
  (set-value :vocabulary vocabulary-id)
  (set-value :prefix_meta_key meta-key-id))

(defn- available-prefix-rows []
  (let [values (:values @prefix-values*)
        id (entity-id)]
    (for [vocabulary (:vocabularies @form-data*)
          meta-key (get @form-data* (:id vocabulary))
          :when (presence (:id meta-key))
          :let [value (get values (:id meta-key))]]
      {:vocabulary-id (:id vocabulary)
       :vocabulary-label (:label vocabulary)
       :meta-key-id (:id meta-key)
       :meta-key-label (:label meta-key)
       :value (presence (:value value))
       :folder-name (or (:folder_name value) (str "_" id))})))

(defn- matches-filter? [term row]
  (or (empty? term)
      (some #(clojure.string/includes? (clojure.string/lower-case (str %)) term)
            ((juxt :vocabulary-label :meta-key-label :meta-key-id :value) row))))

(defn- column-label [k]
  (clojure.string/replace (i18n/t k) #"\s*[:：]\s*$" ""))

(defn- available-prefix-row [row selected?]
  [:tr {:class (when selected? "info")
        :on-click #(choose-prefix (:vocabulary-id row) (:meta-key-id row))}
   [:td (:vocabulary-label row)]
   [:td
    (:meta-key-label row)
    [:br]
    [:code.meta-key-id (:meta-key-id row)]]
   [:td.prefix-value {:title (:value row)}
    (or (:value row)
        [:span.text-muted (i18n/t :download/available-prefixes-no-value)])]
   [:td.folder-name [:code (:folder-name row)]]])

(defn- prefix-values-status []
  (case (:status @prefix-values*)
    :loading [:span " " (i18n/t :download/available-prefixes-values-loading)]
    :failed [:span.text-warning " " (i18n/t :download/available-prefixes-values-failed)]
    nil))

(defn- available-prefixes-table []
  (let [term (clojure.string/lower-case (clojure.string/trim @available-prefixes-filter*))
        rows (cond->> (filter (partial matches-filter? term) (available-prefix-rows))
               @only-with-value* (filter :value))
        selected (-> @form-data* :prefix_meta_key)
        loading (count @loading-vocabularies*)
        failed @failed-vocabularies*]
    [:div.available-prefixes
     [:p (i18n/t :download/available-prefixes-entity
                 {:title (or (-> @download* :entity :title presence)
                             (entity-id))})]
     [:div.available-prefixes-controls
      [:input.form-control.input-sm
       {:type :search
        :placeholder (i18n/t :download/available-prefixes-filter)
        :value @available-prefixes-filter*
        :on-change #(reset! available-prefixes-filter* (.. % -target -value))}]
      [:label.checkbox-inline
       [:input {:type :checkbox
                :checked @only-with-value*
                :on-change #(swap! only-with-value* not)}]
       " " (i18n/t :download/available-prefixes-only-with-value)]]
     [:p.help-block
      (i18n/t :download/available-prefixes-help {:count (count rows)})
      (when (pos? loading)
        [:span " " (i18n/t :download/available-prefixes-loading {:count loading})])
      [prefix-values-status]]
     (when (seq failed)
       [:p.text-warning
        (i18n/t :download/available-prefixes-failed
                {:vocabularies (clojure.string/join ", " (sort failed))})])
     [:div.available-prefixes-list
      [:table.table.table-condensed.table-hover
       [:thead
        [:tr
         [:th (column-label :download/vocabulary)]
         [:th (column-label :download/meta-key)]
         [:th (i18n/t :download/available-prefixes-value)]
         [:th (i18n/t :download/available-prefixes-folder-name)]]]
       [:tbody
        (when (and (empty? term) (not @only-with-value*))
          [:tr {:class (when-not (presence selected) "info")
                :on-click #(set-value :prefix_meta_key "")}
           [:td {:col-span 3} [:em (i18n/t :download/no-meta-key-prefix)]]
           [:td.folder-name [:code (entity-id)]]])
        (for [row rows]
          ^{:key (:meta-key-id row)}
          [available-prefix-row row (= selected (:meta-key-id row))])]]]]))

(defn available-prefixes-component []
  [:div.form-group.available-prefixes-toggle
   [:button.btn.btn-default.btn-sm
    {:type :button
     :on-click toggle-available-prefixes}
    [:span.glyphicon
     {:class (if @show-available-prefixes*
               "glyphicon-chevron-down"
               "glyphicon-chevron-right")
      :aria-hidden "true"}]
    " "
    (i18n/t (if @show-available-prefixes*
              :download/available-prefixes-hide
              :download/available-prefixes-show))]
   (when @show-available-prefixes*
     [available-prefixes-table])])


;;; prefix vocabulary ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn load-vocabularies []
  (when-not (-> @form-data* :vocabularies)
    (request/send-off
      {:method :get
       :path "/vocabularies/"}
      {:title (i18n/t :download/fetch-vocabularies)}
      :callback (fn [req]
                  (set-value :vocabularies
                             (->> (:body req)
                                  (map #(assoc % :key (:id %)))
                                  (sort-by :label)))
                  (when @show-available-prefixes*
                    (doseq [vocabulary (:vocabularies @form-data*)]
                      (load-meta-keys (:id vocabulary))))))))

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

(defn- prefix-info-link []
  (let [label (i18n/t :download/prefix-info)]
    [:a.prefix-info
     {:href "/help"
      :title label
      :on-click (fn [e]
                  (.preventDefault e)
                  (reset! help/scroll-to* help/prefix-examples-id)
                  (accountant/navigate! "/help"))}
     [:span.glyphicon.glyphicon-info-sign {:aria-hidden "true"}]
     [:span.sr-only label]]))

(defn prefix-component []
  [:div.prefix
   [:h4 (i18n/t :download/prefix) " " [prefix-info-link]]
   [:p (i18n/t :download/prefix-help)]
   [prefix-vocabulary-component]
   [prefix-meta-key-component]
   [available-prefixes-component]
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
    (set-value :prefix_meta_key "madek_core:title"))
  (when @show-available-prefixes*
    (load-prefix-values)))

(defn component []
  (reagent/create-class
    {;:component-will-mount #(swap! state/client-db assoc-in [:download :download-form] {})
     :component-did-mount initialize-form-data
     :render main-component }))
