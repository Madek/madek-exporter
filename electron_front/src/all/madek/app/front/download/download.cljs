(ns madek.app.front.download.download
  (:refer-clojure :exclude [str])
  (:require
    [madek.app.front.utils :refer [str presence]]
    [madek.app.front.state :as state]
    [madek.app.front.i18n :as i18n]
    [madek.app.front.request :as request]
    [madek.app.front.connection :as connection]

    [clojure.string :as string]
    [inflections.core :refer [pluralize]]

    [cljs.nodejs :as nodejs]
    [reagent.core :as reagent]
    [reagent.ratom :as ratom :refer [reaction]]
    ))

(def Electron (nodejs/require "electron"))
(def shell (.-shell Electron))

(def download* (reaction (-> @state/jvm-main-db :download)))

(defn item-medienarchiv-url [item]
  (when-let [base (-> @state/jvm-main-db :connection :url presence)]
    (when-let [path (case (:type item)
                      "Collection" "/sets/"
                      "MediaEntry" "/entries/"
                      nil)]
      (str base path (:id item)))))

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

(defn item-progress-value [item]
  "0.0–1.0 for an item; back-compat when :progress is missing."
  (cond
    (number? (:progress item)) (max 0.0 (min 1.0 (double (:progress item))))
    (= "passed" (:state item)) 1.0
    :else 0.0))

(defn item-progress-percent [item]
  (js/Math.round (* 100 (item-progress-value item))))

(defn progress-percent []
  (let [items (items-seq)
        total (count items)
        finished? (:download-finished @download*)]
    (cond
      (and finished? (zero? total)) 100
      (zero? total) 0
      :else (js/Math.round
             (* 100 (/ (reduce + 0 (map item-progress-value items))
                       total))))))

(defn current-downloading-items []
  (->> (items-seq)
       (filter #(= "downloading" (:state %)))
       (sort-by (fn [item] (or (:download_started-at item) "")))))

(defn current-downloading-item []
  (last (current-downloading-items)))

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
    :cancelled [:span.glyphicon.glyphicon-remove-circle.text-warning {:aria-hidden "true"}]
    [:span.glyphicon.glyphicon-unchecked.text-muted {:aria-hidden "true"}]))

(defn progress-component []
  (let [{:keys [total passed sets media-entries]} (item-counts)
        pct (progress-percent)
        finished? (:download-finished @download*)
        cancelled? (:download-cancelled @download*)]
    [:div.progress
     [:div.progress-bar
      {:class (cond
                (not finished?) "progress-bar-info active"
                cancelled? "progress-bar-warning"
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
      " ("
      (i18n/t :download/item-progress {:percent pct})
      ") — "
      (pluralize sets "Set") ", "
      (pluralize media-entries "MediaEntry")]]))

(defn current-activity-component []
  (when-not (:download-finished @download*)
    (let [active (current-downloading-items)
          n (count active)
          {:keys [passed total]} (item-counts)]
      (when (pos? n)
        (if (= 1 n)
          (let [item (first active)]
            [:p.download-current
             (i18n/t :download/current
                     {:type (item-type-label item)
                      :title (item-label item)
                      :passed passed
                      :total total})])
          [:p.download-current
           (i18n/t :download/current-many
                   {:count n
                    :passed passed
                    :total total})])))))

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
  (let [dl-done? (boolean (or (:download-finished @download*)
                              (:download-cancelled @download*)))
        status (cond
                 (= "passed" (:state item)) :done
                 (and dl-done? (not= "passed" (:state item))) :cancelled
                 (= "downloading" (:state item)) :active
                 :else :pending)
        files-n (media-files-count item)
        title (or (presence (:title item)) "—")
        uuid (str (:id item))
        item-pct (item-progress-percent item)
        detail (cond
                 (= status :active)
                 (str (i18n/t :download/item-progress {:percent item-pct})
                      (when (pos? files-n)
                        (str " · " (i18n/t :download/files-count {:count files-n}))))
                 (= status :done)
                 (i18n/t :download/item-progress {:percent 100})
                 (= status :cancelled)
                 (str (i18n/t :download/item-progress {:percent item-pct}))
                 :else nil)]
    [:li {:key uuid
          :class (str "checklist-item status-" (name status))}
     [status-icon status]
     [:span.checklist-label.item-columns
      [:span.item-type
       [:span.label
        {:class (if (= "Collection" (:type item))
                  "label-primary"
                  "label-default")}
        (item-type-label item)]]
      [:span.item-title {:title title} title]
      (if-let [url (item-medienarchiv-url item)]
        [:a.item-id.text-muted
         {:href url
          :title uuid
          :on-click (fn [e]
                      (.preventDefault e)
                      (.openExternal shell url))}
         uuid]
        [:span.item-id.text-muted {:title uuid} uuid])
      [:span.item-detail.text-muted detail]]]))

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

(defn clear-export-steps []
  (let [req {:method :patch
             :json-params
             {:step1-completed false
              :step2-completed false
              :download-started false
              :download-finished false
              :download-cancelled false
              :cancel-requested false
              :items nil
              :errors nil
              :logs nil
              :data-check nil}
             :path "/download"}]
    (request/send-off
      req {:title (i18n/t :download/dismiss-req)})))

(defn cancel-download []
  (let [req {:method :post
             :path "/download/cancel"}]
    (request/send-off
      req {:title (i18n/t :download/cancel-req)
           :show_request_modal false})))

(defn file-manager-label-key []
  (case (.-platform nodejs/process)
    "darwin" :download/open-finder
    "win32" :download/open-explorer
    :download/open-file-manager))

(defn both-api-mode? []
  (= :both (keyword (-> @state/jvm-main-db :connection :api-mode))))

(defn open-target-directory [e]
  (.preventDefault e)
  (when-let [dir (presence (:target-directory @download*))]
    (.openPath shell dir)))

(defn check-export-data []
  (let [req {:method :post
             :path "/download/compare"}]
    (request/send-off
      req {:title (i18n/t :download/check-data-req)
           :show_request_modal false
           :show_response_success_modal false})))

(defn- listed-paths [section]
  (or (:items section) []))

(defn- format-key-count-row [row]
  (str (:path row)
       ": api " (:api-key-count row)
       " keys, api-v2 " (:api-v2-key-count row) " keys"))

(defn- format-key-mismatch-row [row]
  (let [missing (or (:missing-keys row) [])
        broken (or (:broken-structure row) [])]
    (str (:path row) " error:\n"
         (string/join
          "\n"
          (concat
           [(str "        api " (:api-key-count row)
                 " keys, api-v2 " (:api-v2-key-count row) " keys")]
           (when-let [err (:error row)]
             [(str "        " err)])
           (map #(str "        missing key " %) missing)
           (when (seq broken)
             ["        broken structure"]))))))

(defn data-check-result-component []
  (when-let [check (:data-check @download*)]
    (let [counts (:counts check)
          equal? (:equal? check)]
      [:div.download-data-check-result
       {:class (if equal? "text-success" "text-danger")}
       (cond
         (not (:ok check))
         [:p [:strong (i18n/t :download/check-data-failed)]
          " " (or (:error check) "")]

         :else
         [:div
          [:p [:strong (if equal?
                         (i18n/t :download/check-data-equal)
                         (i18n/t :download/check-data-diff))]
           " "
           (i18n/t :download/check-data-summary
                   {:api (:api counts)
                    :api-v2 (:api-v2 counts)
                    :common (:common counts)})]
          [:ul.list-unstyled
           [:li (i18n/t :download/check-data-keys
                        {:api (:api-keys counts)
                         :api-v2 (:api-v2-keys counts)})]
           [:li (i18n/t :download/check-data-only-api
                        {:count (:only-in-api counts)})]
           [:li (i18n/t :download/check-data-only-api-v2
                        {:count (:only-in-api-v2 counts)})]
           [:li (i18n/t :download/check-data-key-mismatch
                        {:count (:key-mismatch counts)})]]
          (when (seq (listed-paths (:key-counts check)))
            [:div
             [:h5 (i18n/t :download/check-data-keys-title)]
             [:pre.download-data-check-pre
              (->> (listed-paths (:key-counts check))
                   (map format-key-count-row)
                   (string/join "\n"))]])
          (when (seq (listed-paths (:only-in-api check)))
            [:div
             [:h5 (i18n/t :download/check-data-only-api-title)]
             [:pre.download-data-check-pre
              (string/join "\n" (listed-paths (:only-in-api check)))]])
          (when (seq (listed-paths (:only-in-api-v2 check)))
            [:div
             [:h5 (i18n/t :download/check-data-only-api-v2-title)]
             [:pre.download-data-check-pre
              (string/join "\n" (listed-paths (:only-in-api-v2 check)))]])
          (when (seq (listed-paths (:key-mismatches check)))
            [:div
             [:h5 (i18n/t :download/check-data-hash-title)]
             [:pre.download-data-check-pre
              (->> (listed-paths (:key-mismatches check))
                   (map format-key-mismatch-row)
                   (string/join "\n"))]])])])))
(defn open-folder-component []
  (when (presence (:target-directory @download*))
    [:div.open-download-dir
     [:div.open-download-dir-actions
      [:button.btn.btn-default
       {:type "button"
        :on-click open-target-directory}
       [:span.glyphicon.glyphicon-folder-open {:aria-hidden "true"}]
       (i18n/t (file-manager-label-key))]
      (when (and (both-api-mode?)
                 (or (:download-finished @download*)
                     (:download-cancelled @download*)))
        [:button.btn.btn-default
         {:type "button"
          :on-click check-export-data}
         [:span.glyphicon.glyphicon-check {:aria-hidden "true"}]
         (i18n/t :download/check-data)])]
     [data-check-result-component]]))

(defn- log-line-api-tag [line]
  (when (and (string? line) (not-empty line))
    (cond
      (re-find #"/api-v2(/|\?|$)" line) :api-v2
      (re-find #"/api(/|\?|$)" line) :api
      :else nil)))

(defn- log-line-visible? [line filter-mode]
  (case filter-mode
    :both true
    :api (= :api (log-line-api-tag line))
    :api-v2 (= :api-v2 (log-line-api-tag line))
    true))

(defn- filter-log-lines [lines filter-mode]
  (vec (filter #(log-line-visible? % filter-mode) lines)))

(defn- logs-filter-buttons [filter*]
  [:div.download-logs-filter
   [:span.download-logs-filter-label (i18n/t :download/logs-filter-label)]
   (for [mode [:both :api :api-v2]]
     ^{:key mode}
     [:button.btn.btn-sm.download-logs-filter-btn
      {:type "button"
       :class (when (= @filter* mode) "active")
       :aria-pressed (= @filter* mode)
       :on-click #(reset! filter* mode)}
      (i18n/t (keyword (str "download/logs-filter-" (name mode))))])])

(defn logs-component []
  (let [open?* (reagent/atom false)
        auto-opened?* (reagent/atom false)
        filter* (reagent/atom :both)]
    (fn []
      (let [logs (or (:logs @download*) [])
            n (count logs)
            visible (filter-log-lines logs @filter*)
            n-visible (count visible)
            finished? (boolean (:download-finished @download*))]
        (when (and finished? (pos? n) (not @auto-opened?*))
          (reset! auto-opened?* true)
          (reset! open?* true))
        [:div.download-logs
         [:button.btn.btn-link.download-logs-toggle
          {:type "button"
           :aria-expanded (boolean @open?*)
           :on-click #(swap! open?* not)}
          [:span.glyphicon
           {:class (if @open?*
                     "glyphicon-chevron-down"
                     "glyphicon-chevron-right")
            :aria-hidden "true"}]
          (i18n/t :download/logs)
          (when (pos? n)
            [:span.text-muted
             (if (and (not= @filter* :both) (not= n-visible n))
               (str " (" n-visible "/" n ")")
               (str " (" n ")"))])]
         (when @open?*
           (when (pos? n)
             [logs-filter-buttons filter*])
           (cond
             (zero? n)
             [:p.text-muted.download-logs-empty
              (i18n/t :download/logs-empty)]

             (zero? n-visible)
             [:p.text-muted.download-logs-empty
              (i18n/t :download/logs-filter-empty)]

             :else
             [:pre.download-logs-text
              (string/join "\n" visible)]))]))))

(defn downloading-component []
  [:div
   [:h2 (i18n/t :download/downloading-now)]
   [progress-detail-component]
   [logs-component]
   [open-folder-component]
   [:div.dismiss.download-cancel
    [:button.btn.btn-warning
     {:on-click cancel-download}
     (i18n/t :download/cancel)]]])

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
   [:h2 (if (:download-cancelled @download*)
          (i18n/t :download/cancelled)
          (i18n/t :download/finished))]
   [progress-detail-component]
   [logs-component]
   [open-folder-component]
   [errors-component]
   [dismiss-component]])
