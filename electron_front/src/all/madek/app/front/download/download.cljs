(ns madek.app.front.download.download
  (:refer-clojure :exclude [str])
  (:require
    [madek.app.front.utils :refer [str presence]]
    [madek.app.front.state :as state]
    [madek.app.front.i18n :as i18n]
    [madek.app.front.request :as request]
    [madek.app.front.connection :as connection]

    [inflections.core :refer [pluralize]]

    [reagent.ratom :as ratom :refer [reaction]]
    ))

(def download* (reaction (-> @state/jvm-main-db :download)))

;;; progress helpers

(defn items-seq []
  (->> (or (@download* :items) {})
       (map (fn [[id item]] (assoc item :id (str id))))))

(defn item-counts []
  (let [items (items-seq)]
    {:total (count items)
     :passed (->> items (filter #(= "passed" (:state %))) count)
     :sets (->> items (filter #(= "Collection" (:type %))) count)
     :media-entries (->> items (filter #(= "MediaEntry" (:type %))) count)}))

(defn progress-percent []
  (let [{:keys [total passed]} (item-counts)
        finished? (:download-finished @download*)]
    (cond
      (and finished? (zero? total)) 100
      (zero? total) 0
      :else (js/Math.round (* 100 (/ passed total))))))

(defn current-downloading-item []
  (->> (items-seq)
       (filter #(= "downloading" (:state %)))
       (sort-by (fn [item] (or (:download_started-at item) "")))
       last))

(defn sorted-items []
  (let [rank (fn [item]
               (cond
                 (= "downloading" (:state item)) 0
                 (= "passed" (:state item)) 2
                 :else 1))]
    (->> (items-seq)
         (sort-by (fn [item]
                    [(rank item)
                     (or (:download_started-at item) "")
                     (or (:id item) "")])))))

(defn truncate-id [id]
  (let [s (str id)]
    (if (> (count s) 8)
      (str (subs s 0 8) "…")
      s)))

(defn item-label [item]
  (or (presence (:title item))
      (truncate-id (:id item))))

(defn item-type-label [item]
  (case (:type item)
    "Collection" (i18n/t :download/type-set)
    "MediaEntry" (i18n/t :download/type-media-entry)
    (or (:type item) "?")))

(defn media-files-count [item]
  (count (or (:media-files item) {})))

(defn phase-status [phase-key]
  (let [dl @download*
        items (items-seq)
        media-entries (filter #(= "MediaEntry" (:type %)) items)
        finished? (:download-finished dl)
        started? (:download-started dl)]
    (case phase-key
      :started
      (cond finished? :done
            started? :done
            :else :pending)

      :entities
      (cond finished? :done
            (seq items) (if (some #(= "downloading" (:state %)) items)
                          :active
                          :done)
            started? :active
            :else :pending)

      :media-entries
      (cond
        finished? :done
        (empty? media-entries) :pending
        (every? #(= "passed" (:state %)) media-entries) :done
        :else :active)

      :finished
      (cond finished? :done
            started? :pending
            :else :pending)

      :pending)))

(defn phase-defs []
  [{:key :started :label-key :download/phase-started}
   {:key :entities :label-key :download/phase-entities}
   {:key :media-entries :label-key :download/phase-media-entries}
   {:key :finished :label-key :download/phase-finished}])

;;; UI components

(defn errors-component []
  [:div.errors
   (when (-> @download* :errors empty? not)
     [:h3 (i18n/t :download/errors)]
     (doall (for [[ek ev] (-> @download* :errors)]
              [:div.panel.panel-danger
               {:key (str ek)}
               [:div.panel-heading
                [:h3.panel-title ek]]
               [:div.panel-body
                [:pre.wrap ev]]])))])

(defn status-icon [status]
  (case status
    :done [:span.glyphicon.glyphicon-ok.text-success {:aria-hidden "true"}]
    :active [:span.glyphicon.glyphicon-refresh.spinning {:aria-hidden "true"}]
    [:span.glyphicon.glyphicon-unchecked.text-muted {:aria-hidden "true"}]))

(defn progress-component []
  (let [{:keys [total passed sets media-entries]} (item-counts)
        pct (progress-percent)
        finished? (:download-finished @download*)]
    [:div.progress
     [:div.progress-bar
      {:class (cond
                (not finished?) "progress-bar-info active"
                (-> @download* :errors empty?) "progress-bar-success"
                :else "progress-bar-danger")
       :role "progressbar"
       :aria-valuenow pct
       :aria-valuemin 0
       :aria-valuemax 100
       :style {:width (str pct "%")
               :min-width (when (and (not finished?) (pos? total)) "2em")}}
      (if finished?
        (i18n/t :download/downloaded)
        (i18n/t :download/downloading))
      (i18n/t :download/progress-of {:passed passed :total total})
      " — "
      (pluralize sets "Set") ", "
      (pluralize media-entries "MediaEntry")]]))

(defn current-activity-component []
  (when-not (:download-finished @download*)
    (when-let [item (current-downloading-item)]
      (let [{:keys [passed total]} (item-counts)]
        [:p.download-current
         (i18n/t :download/current
                 {:type (item-type-label item)
                  :title (item-label item)
                  :passed passed
                  :total total})]))))

(defn phase-checklist-component []
  [:div.download-phases
   [:h4 (i18n/t :download/phases-title)]
   [:ul.list-unstyled.download-checklist
    (doall
      (for [{:keys [key label-key]} (phase-defs)
            :let [status (phase-status key)]]
        [:li {:key (name key)
              :class (str "checklist-item status-" (name status))}
         [status-icon status]
         [:span.checklist-label (i18n/t label-key)]]))]])

(defn item-row [item]
  (let [status (case (:state item)
                 "passed" :done
                 "downloading" :active
                 :pending)
        files-n (media-files-count item)]
    [:li {:key (:id item)
          :class (str "checklist-item status-" (name status))}
     [status-icon status]
     [:span.checklist-label
      [:span.label
       {:class (if (= "Collection" (:type item))
                 "label-primary"
                 "label-default")}
       (item-type-label item)]
      " "
      [:span.item-id.text-muted (:id item)]
      (when (presence (:title item))
        [:span.item-title " — " (:title item)])
      (when (and (= status :active) (pos? files-n))
        [:span.item-detail.text-muted
         " — "
         (i18n/t :download/files-count {:count files-n})])]]))

(defn item-checklist-component []
  (let [items (sorted-items)]
    (when (seq items)
      [:div.download-items
       [:h4 (i18n/t :download/items-title)]
       [:ul.list-unstyled.download-checklist.download-items-list
        (doall (for [item items]
                 ^{:key (:id item)}
                 [item-row item]))]])))

(defn progress-detail-component []
  [:div.download-progress-detail
   [progress-component]
   [current-activity-component]
   [phase-checklist-component]
   [item-checklist-component]])

(defn downloading-component []
  [:div
   [:h2 (i18n/t :download/downloading-now)]
   [progress-detail-component]])

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
   [progress-detail-component]
   [errors-component]
   [dismiss-component]])
