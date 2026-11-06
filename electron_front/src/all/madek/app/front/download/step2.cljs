(ns madek.app.front.download.step2
  (:refer-clojure :exclude [str keyword])
  (:require
    [madek.app.front.utils :refer [str keyword deep-merge presence]]
    [madek.app.front.utils.form :as form-utils]
    [madek.app.front.i18n :as i18n]
    [madek.app.front.request :as request]

    [clojure.string]
    [cljs.core.async :refer [chan]]
    [reagent.core :as reagent]
    [fipp.edn :refer [pprint]]
    [reagent.ratom :as ratom :refer [reaction]]
    [madek.app.front.state :as state]
    [madek.app.front.help :as help]
    [madek.app.front.download.download :refer [entity-export-line-component]]
    [madek.app.front.env]
    [accountant.core :as accountant]
    [inflections.core :as inflections]
    )
  (:import
    [goog Uri]
    ))

(def form-data* (reaction (-> @state/client-db :download :download-form)))

(def download* (reaction (-> @state/jvm-main-db :download)))

(def set-value
  (form-utils/create-update-form-data-setter
    state/client-db
    [:download :download-form]))

(declare abort-prefix-options-request!)

(defn back []
  (abort-prefix-options-request! :idle)
  (let [req {:method :patch
             :json-params {:step1-completed false}
             :path "/download"}]
    (request/send-off
      req {:title (i18n/t :download/back-step1-req)})))

(defn submit []
  (abort-prefix-options-request! :continued)
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


;;; prefix picker ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce prefix-dropdown-open* (reagent/atom false))

(defonce prefix-options-filter* (reagent/atom ""))

(defonce only-with-value* (reagent/atom true))

(defonce prefix-help-expanded* (reagent/atom false))

;; {:entity-id "…" :status :idle|:loading|:loaded|:failed|:continued
;;  :fetched true once a load has succeeded :options […] :entity-title "…"}
(defonce prefix-options* (reagent/atom {:status :idle}))

;; Bumped whenever the in-flight prefix-options request is aborted, so a late
;; response cannot overwrite a newer load or a step the user already left.
(defonce prefix-load-gen* (atom 0))

;; {:id request-id :cancel chan} for the XHR currently loading prefix options.
(defonce prefix-request* (atom nil))

(defn- entity-id []
  (-> @download* :entity :uuid))

(defn abort-prefix-options-request!
  "Abort the browser request for prefix options. The server stops the Madek
  walk when this generation is superseded or step 2 is left.
  `status` replaces :loading so the spinner does not keep running."
  ([] (abort-prefix-options-request! nil))
  ([status]
   (swap! prefix-load-gen* inc)
   (when-let [{:keys [id cancel]} @prefix-request*]
     (reset! prefix-request* nil)
     (request/abort! id cancel))
   (when status
     (swap! prefix-options* assoc :status status)
     (reset! prefix-dropdown-open* false))))

(defn- prefix-options-fetched?
  "True after one successful load for this entity. Returning from step 3
  must not start another automatic request."
  [id]
  (let [state @prefix-options*]
    (and (= id (:entity-id state))
         (true? (:fetched state)))))

(defn- load-prefix-options
  ([] (load-prefix-options false))
  ([force?]
   (let [id (entity-id)]
     (cond
       (not id) nil

       (and (not force?) (prefix-options-fetched? id))
       (when-not (#{:loading :loaded} (:status @prefix-options*))
         (swap! prefix-options* assoc :status :loaded))

       (and (not force?)
            (= id (:entity-id @prefix-options*))
            (= :loading (:status @prefix-options*)))
       nil

       :else
       (do
         (abort-prefix-options-request!)
         (let [gen @prefix-load-gen*
               cancel (chan)]
           (reset! prefix-options* {:entity-id id :status :loading})
           (reset! prefix-dropdown-open* false)
           (reset! prefix-request*
                   {:cancel cancel
                    :id (request/send-off
                          {:method :get
                           :path "/download/prefix-options"
                           :cancel cancel}
                          {:title (i18n/t :download/fetch-prefix-options)}
                          :callback (fn [resp]
                                      (when (= gen @prefix-load-gen*)
                                        (reset! prefix-request* nil)
                                        (reset! prefix-options*
                                                (if (:success resp)
                                                  {:entity-id id
                                                   :status :loaded
                                                   :fetched true
                                                   :entity-title (-> resp :body :entity_title)
                                                   :options (vec (or (-> resp :body :options) []))}
                                                  {:entity-id id :status :failed})))))})))))))

(defn- choose-prefix [meta-key-id]
  (set-value :prefix_meta_key (or meta-key-id ""))
  (reset! prefix-dropdown-open* false))

(defn- selected-option []
  (let [selected (presence (-> @form-data* :prefix_meta_key))]
    (when selected
      (some #(when (= selected (:meta_key_id %)) %)
            (:options @prefix-options*)))))

(defn- column-label [k]
  (clojure.string/replace (i18n/t k) #"\s*[:：]\s*$" ""))

(defn- matches-filter? [term option]
  (or (empty? term)
      (some #(clojure.string/includes? (clojure.string/lower-case (str %)) term)
            ((juxt :vocabulary_label :meta_key_label :meta_key_id :value) option))))

(defn- filtered-options []
  (let [term (clojure.string/lower-case (clojure.string/trim @prefix-options-filter*))]
    (cond->> (filter (partial matches-filter? term) (:options @prefix-options*))
      @only-with-value* (filter #(presence (:value %))))))

(defn- prefix-option-row [option selected?]
  [:tr {:class (when selected? "info")
        :role "option"
        :aria-selected selected?
        :on-click #(choose-prefix (:meta_key_id option))}
   [:td (:vocabulary_label option)]
   [:td
    (:meta_key_label option)
    [:br]
    [:code.meta-key-id (:meta_key_id option)]]
   [:td.prefix-value {:title (:value option)}
    (or (:value option)
        [:span.text-muted (i18n/t :download/available-prefixes-no-value)])]
   [:td.folder-name [:code (:folder_name option)]]])

(defn- prefix-options-status-icon [status]
  (case status
    :loading
    [:span.prefix-options-status {:aria-hidden "true"}
     [:span.glyphicon.glyphicon-refresh.spinning]]
    :loaded
    [:span.prefix-options-status {:aria-hidden "true"}
     [:span.glyphicon.glyphicon-ok.text-success]]
    :continued
    [:span.prefix-options-status {:aria-hidden "true"}
     [:span.glyphicon.glyphicon-ok.prefix-options-continued]]
    :failed
    [:span.prefix-options-status {:aria-hidden "true"}
     [:span.glyphicon.glyphicon-remove-circle.text-warning]]
    nil))

(defn- prefix-dropdown-summary []
  (let [status (:status @prefix-options*)
        none (i18n/t :download/no-meta-key-prefix)]
    (cond
      (= :failed status)
      [:span.prefix-dropdown-placeholder.text-warning
       (i18n/t :download/prefix-options-failed)]
      (or (= :loading status)
          (not (presence (-> @form-data* :prefix_meta_key))))
      [:span.prefix-dropdown-placeholder [:em none]]
      :else
      (if-let [option (selected-option)]
        [:span.prefix-dropdown-summary
         [:span {:title (:vocabulary_label option)} (:vocabulary_label option)]
         [:span {:title (:meta_key_label option)} (:meta_key_label option)]
         [:span {:title (:value option)}
          (or (:value option)
              (i18n/t :download/available-prefixes-no-value))]]
        [:span.prefix-dropdown-placeholder
         [:code (-> @form-data* :prefix_meta_key)]]))))

(defn- prefix-dropdown-menu []
  (let [term (clojure.string/lower-case (clojure.string/trim @prefix-options-filter*))
        rows (filtered-options)
        selected (presence (-> @form-data* :prefix_meta_key))
        title (or (:entity-title @prefix-options*)
                  (-> @download* :entity :title presence)
                  (entity-id))]
    [:div.prefix-dropdown-menu {:role "listbox"}
     [:p (i18n/t :download/available-prefixes-entity {:title title})]
     [:div.prefix-dropdown-controls
      [:input.form-control.input-sm
       {:type :search
        :placeholder (i18n/t :download/available-prefixes-filter)
        :value @prefix-options-filter*
        :on-change #(reset! prefix-options-filter* (.. % -target -value))}]
      [:label.checkbox-inline
       [:input {:type :checkbox
                :checked @only-with-value*
                :on-change #(swap! only-with-value* not)}]
       " " (i18n/t :download/available-prefixes-only-with-value)]]
     [:p.help-block
      (i18n/t :download/available-prefixes-help {:count (count rows)})]
     [:div.prefix-dropdown-list
      [:table.table.table-condensed.table-hover
       [:thead
        [:tr
         [:th (column-label :download/vocabulary)]
         [:th (column-label :download/meta-key)]
         [:th (i18n/t :download/available-prefixes-value)]
         [:th (i18n/t :download/available-prefixes-folder-name)]]]
       [:tbody
        (when (and (empty? term) (not @only-with-value*))
          [:tr.no-prefix {:class (when-not selected "info")
                          :role "option"
                          :aria-selected (not selected)
                          :on-click #(choose-prefix "")}
           [:td {:col-span 3} [:em (i18n/t :download/no-meta-key-prefix)]]
           [:td.folder-name [:code (entity-id)]]])
        (for [option rows]
          ^{:key (:meta_key_id option)}
          [prefix-option-row option (= selected (:meta_key_id option))])]]]]))

(defn- usable-file-name [s]
  "Same rule as JVM useableFileName: keep a–z A–Z 0–9 and spaces."
  (clojure.string/replace (str s) #"[^a-zA-Z0-9 ]" ""))

(defn- selected-folder-name []
  (if-let [option (selected-option)]
    (or (:folder_name option) (str "_" (entity-id)))
    (entity-id)))

(defn- name-with-prefix [folder-name]
  (let [id (str (entity-id))]
    (if (and (presence folder-name)
             (presence id)
             (clojure.string/ends-with? folder-name id)
             (not= folder-name id))
      (let [prefix (subs folder-name 0 (- (count folder-name) (count id)))]
        [:span [:span.prefix-part prefix] id])
      folder-name)))

(defn- export-root-name []
  (or (some-> @download* :target-directory
              (clojure.string/split #"[\\/]")
              last
              presence)
      "export"))

(defn- example-item [depth kind content]
  [:div.prefix-example-item {:class (str "depth-" depth)}
   [:span.glyphicon
    {:class (if (= kind :folder) "glyphicon-folder-close" "glyphicon-file")
     :aria-hidden "true"}]
   " "
   [:code content]])

(defn- prefix-example-tree []
  (let [folder (selected-folder-name)
        labeled (name-with-prefix folder)
        skip-files? (-> @form-data* :skip_media_files)
        collection? (= :collection (-> @download* :entity :type))]
    [:div.prefix-example-tree
     [example-item 0 :folder (str (export-root-name) "/")]
     [example-item 1 :file "meta-data_schema.json"]
     [example-item 1 :folder [:span labeled "/"]]
     [example-item 2 :file "index.html"]
     [example-item 2 :file "meta-data.json"]
     [example-item 2 :file [:span labeled "_meta-data.json"]]
     (when collection?
       [example-item 2 :folder (i18n/t :download/prefix-example-descendants)])
     (when-not skip-files?
       [example-item (if collection? 3 2) :folder "media-files/"])]))

(defn- prefix-help-details []
  (let [value (presence (:value (selected-option)))
        cleaned (when value (usable-file-name value))]
    [:div.prefix-help-details
     [:p.help-block (i18n/t :download/prefix-sanitize-help)]
     (when (and value (not= value cleaned))
       [:p.help-block
        [:code value] " → " [:code cleaned]])
     [:p.help-block
      (i18n/t :download/meta-key-help-1-before)
      [:code (i18n/t :download/no-meta-key-prefix)]
      (i18n/t :download/meta-key-help-1-after)]
     [:p.help-block (i18n/t :download/meta-key-help-2)]]))

(defn- prefix-help-toggle []
  (let [expanded? @prefix-help-expanded*
        label (i18n/t (if expanded? :download/prefix-help-less :download/prefix-help-more))]
    [:div.prefix-help-toggle
     [:button.btn.btn-link.btn-sm
      {:type :button
       :aria-expanded expanded?
       :on-click #(swap! prefix-help-expanded* not)}
      label]
     (when expanded?
       [prefix-help-details])]))

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

(defn- prefix-example-component []
  (when (= :loaded (:status @prefix-options*))
    (let [title (or (:entity-title @prefix-options*)
                    (-> @download* :entity :title presence)
                    (entity-id))]
      [:div.prefix-example
       [:p.prefix-example-heading
        (i18n/t :download/prefix-example-title {:title title})]
       [prefix-example-tree]])))

(defn- close-prefix-dropdown-on-outside-click [e]
  (when-let [el (.querySelector js/document ".prefix-picker")]
    (when-not (.contains el (.-target e))
      (reset! prefix-dropdown-open* false))))

(defn- close-prefix-dropdown-on-escape [e]
  (when (= "Escape" (.-key e))
    (reset! prefix-dropdown-open* false)))

(defn- prefix-picker-render []
  (let [status (:status @prefix-options*)
        loaded? (= :loaded status)
        open? (and loaded? @prefix-dropdown-open*)]
    [:div.form-group.prefix-picker
     {:class (when open? "open")}
     [:div.prefix-dropdown-headers
      [:span (column-label :download/vocabulary)]
      [:span (column-label :download/meta-key)]
      [:span (i18n/t :download/available-prefixes-value)]]
     [:div.prefix-dropdown-control
      [prefix-options-status-icon status]
      [:button.btn.btn-default.form-control.prefix-dropdown-toggle
       {:type :button
        :aria-label (i18n/t :download/prefix)
        :aria-haspopup "listbox"
        :aria-expanded (boolean open?)
        :disabled (= :loading status)
        :on-click (fn [e]
                    (.stopPropagation e)
                    (cond
                      loaded? (swap! prefix-dropdown-open* not)
                      (= :failed status) (load-prefix-options)))}
       [prefix-dropdown-summary]
       [:span.caret]]
      [:button.btn.btn-default.prefix-refresh
       {:type :button
        :title (if (= :loading status)
                 (i18n/t :download/prefix-pause)
                 (i18n/t :download/prefix-refresh))
        :aria-label (if (= :loading status)
                      (i18n/t :download/prefix-pause)
                      (i18n/t :download/prefix-refresh))
        :on-click (fn [e]
                    (.preventDefault e)
                    (.stopPropagation e)
                    (if (= :loading (:status @prefix-options*))
                      (abort-prefix-options-request!
                       (if (seq (:options @prefix-options*)) :loaded :idle))
                      (load-prefix-options true)))}
       [:span.glyphicon
        {:class (if (= :loading status)
                  "glyphicon-pause"
                  "glyphicon-refresh")
         :aria-hidden "true"}]]]
     (when open?
       [prefix-dropdown-menu])
     [prefix-example-component]
     [prefix-help-toggle]]))

(defn prefix-picker-component []
  (reagent/create-class
    {:component-did-mount
     (fn [_]
       (load-prefix-options)
       (.addEventListener js/document "click" close-prefix-dropdown-on-outside-click)
       (.addEventListener js/document "keydown" close-prefix-dropdown-on-escape))
     :component-will-unmount
     (fn [_]
       (abort-prefix-options-request!)
       (.removeEventListener js/document "click" close-prefix-dropdown-on-outside-click)
       (.removeEventListener js/document "keydown" close-prefix-dropdown-on-escape))
     :reagent-render prefix-picker-render}))


;;; prefix ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn prefix-component []
  [:div.prefix
   [:h4 (i18n/t :download/prefix) " " [prefix-info-link]]
   [:p (i18n/t :download/prefix-help)]
   [prefix-picker-component]
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
   [entity-export-line-component]
   [form-component]
   [debug-component]])

(defn initialize-form-data []
  (when-not (contains? @form-data* :prefix_meta_key)
    (set-value :prefix_meta_key ""))
  (load-prefix-options))

(defn component []
  (reagent/create-class
    {:component-did-mount initialize-form-data
     :render main-component }))
