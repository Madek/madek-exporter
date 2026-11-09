(ns madek.app.front.debug
  (:require-macros
    [cljs.core.async.macros :refer [go]])
  (:require
    [cljs-http.client :as http]
    [cljs.core.async :refer [<!]]
    [fipp.edn :refer [pprint]]
    [reagent.core :as reagent]
    [madek.app.front.breadcrumb :as breadcrumb]
    [madek.app.front.i18n :as i18n]
    [madek.app.front.state :as state]
    [clojure.string]
    [clojure.walk]
    ))

(defonce active-tab* (reagent/atom :state))

(defn fix-path [path]
  (clojure.string/replace path #"^/\w:" ""))

(defn state-content []
  [:div.content
   [:div.form
    [:div.form-group
     [:input {:type :checkbox :on-click #(swap! state/client-db assoc :debug (-> @state/client-db :debug not))
              :checked (-> @state/client-db :debug)}]
     (i18n/t :debug/show-per-page)]]

   [:p (i18n/t :debug/electron-main)
    (if (empty? @state/electron-main-db)
      (i18n/t :debug/waiting)
      (i18n/t :debug/connected))]
   [:p (i18n/t :debug/jvm-main)
    (if (:jvm-main-options  @state/jvm-main-db)
      (i18n/t :debug/connected)
      (i18n/t :debug/waiting))]
   [:div.jvm-main-db
    [:h3 "Electron-main DB"]
    [:pre
     (with-out-str (pprint @state/electron-main-db))]]
   [:div.jvm-main-db
    [:h3 "JVM-main DB"]
    [:pre
     (with-out-str (pprint @state/jvm-main-db))]]
   [:div.current-page
    [:h3 "Current Page"]
    [:pre
     (with-out-str (pprint @state/current-page))]]
   [:div.app-db
    [:h3 "Client DB"]
    [:pre
     (with-out-str (pprint @state/client-db))]]
   ])

(defn logs-content []
  (let [logs (-> @state/client-db :app-logs)]
    [:div.content
     [:div.form-group
      [:button.btn.btn-default
       {:on-click #(swap! state/client-db assoc :app-logs [])}
       (i18n/t :debug/clear-logs)]]
     (if (seq logs)
       [:pre
        (with-out-str
          (pprint (reverse logs)))]
       [:div.alert.alert-info
        [:p (i18n/t :debug/no-logs)]])]))

;;; performance ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:private SLOW-MS 1000)

(def ^:private MAX-ROWS 500)

(defonce perf-entries* (reagent/atom []))

(defonce perf-kind* (reagent/atom :all))

(defonce perf-sort-by-duration* (reagent/atom false))

(defonce perf-auto-refresh* (reagent/atom true))

(defn- perf-url []
  (str "http://localhost:"
       (-> @state/electron-main-db :jvm-process :port)
       "/debug/performance"))

(defn- fetch-perf! []
  (go (let [resp (<! (http/get (perf-url) {:headers {"accept" "application/json"}}))]
        (when (:success resp)
          (reset! perf-entries* (vec (-> resp :body :entries)))))))

(defn- clear-perf! []
  (go (<! (http/delete (perf-url)))
      (reset! perf-entries* [])))

(defn- format-time [at]
  (.format (js/moment at) "HH:mm:ss.SSS"))

(defn- visible-entries []
  (let [kind @perf-kind*]
    (cond->> @perf-entries*
      (not= :all kind) (filter #(= (name kind) (:kind %)))
      true reverse
      @perf-sort-by-duration* (sort-by :ms >)
      true (take MAX-ROWS))))

(defn- perf-summary [entries]
  (let [ms (map :ms entries)]
    [:p.text-muted
     (i18n/t :debug/perf-summary
             {:count (count entries)
              :total (reduce + 0 ms)
              :max (if (seq ms) (apply max ms) 0)})]))

(defn- perf-row [{:keys [kind at method url status ms thread]}]
  [:tr {:class (when (>= ms SLOW-MS) "danger")}
   [:td [:code (format-time at)]]
   [:td kind]
   [:td method]
   [:td status]
   [:td {:style {:text-align "right"}} [:strong ms]]
   [:td.text-muted thread]
   [:td {:style {:word-break "break-all"}} url]])

(defn- perf-controls []
  [:div.form-inline {:style {:margin-bottom "1em"}}
   [:select.form-control.input-sm
    {:value (name @perf-kind*)
     :on-change #(reset! perf-kind* (keyword (.. % -target -value)))}
    [:option {:value "all"} (i18n/t :debug/perf-kind-all)]
    [:option {:value "madek"} (i18n/t :debug/perf-kind-madek)]
    [:option {:value "local"} (i18n/t :debug/perf-kind-local)]]
   " "
   [:label.checkbox-inline
    [:input {:type :checkbox
             :checked @perf-sort-by-duration*
             :on-change #(swap! perf-sort-by-duration* not)}]
    " " (i18n/t :debug/perf-sort-by-duration)]
   " "
   [:label.checkbox-inline
    [:input {:type :checkbox
             :checked @perf-auto-refresh*
             :on-change #(swap! perf-auto-refresh* not)}]
    " " (i18n/t :debug/perf-auto-refresh)]
   " "
   [:button.btn.btn-default.btn-sm {:on-click fetch-perf!}
    (i18n/t :debug/perf-refresh)]
   " "
   [:button.btn.btn-default.btn-sm {:on-click clear-perf!}
    (i18n/t :debug/clear-logs)]])

(defn- performance-render []
  (let [entries (visible-entries)]
    [:div.content
     [perf-controls]
     [perf-summary entries]
     (if (seq entries)
       [:table.table.table-condensed.table-hover
        [:thead
         [:tr
          [:th (i18n/t :debug/perf-time)]
          [:th (i18n/t :debug/perf-kind)]
          [:th (i18n/t :debug/perf-method)]
          [:th (i18n/t :debug/perf-status)]
          [:th {:style {:text-align "right"}} "ms"]
          [:th (i18n/t :debug/perf-thread)]
          [:th "URL"]]]
        [:tbody
         (for [entry entries]
           ^{:key (:id entry)} [perf-row entry])]]
       [:div.alert.alert-info
        [:p (i18n/t :debug/perf-empty)]])]))

(defn performance-content []
  (let [timer (atom nil)]
    (reagent/create-class
      {:component-did-mount
       (fn [_]
         (fetch-perf!)
         (reset! timer (js/setInterval #(when @perf-auto-refresh* (fetch-perf!))
                                       1000)))
       :component-will-unmount
       (fn [_]
         (some-> @timer js/clearInterval))
       :reagent-render performance-render})))

(defn- tab [k label]
  [:li {:class (when (= @active-tab* k) "active")}
   [:a {:href "#"
        :on-click (fn [e]
                    (.preventDefault e)
                    (reset! active-tab* k))}
    label]])

(defn content []
  [:div
   [:ul.nav.nav-tabs {:style {:margin-bottom "1em"}}
    [tab :state (i18n/t :debug/state)]
    [tab :logs (i18n/t :debug/app-logs)]
    [tab :performance (i18n/t :debug/performance)]]
   (case @active-tab*
     :logs [logs-content]
     :performance [performance-content]
     [state-content])])

(defn page []
  [:div.page.debug
   [breadcrumb/page-breadcrumb (i18n/t :debug/title)]
   [content]
   ])
