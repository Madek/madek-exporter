(ns madek.app.front.download.step1
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


(def path-regex #"^/(sets)/([0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12})$")

(def form-data* (reaction (-> @state/client-db :download :pre-check-form)))

(def data*
  (reaction
    (when-let [url (-> @form-data* :url presence)]
      (let [uri (Uri. url)
            path (.getPath uri)
            path-dissected (re-matches path-regex path)]
        {:target-directory (:target-directory @form-data*)
         :url url
         :domain (.getDomain uri)
         :schme (.getScheme uri)
         :port (.getPort uri)
         :path path
         :path-dissected path-dissected
         :entity-type (-> path-dissected second inflections/singular keyword)
         :entity-id (-> path-dissected (get 2 nil))
         }))))

(def url-valid?*
  (reaction
    (boolean
      (when-let [connection-url (Uri. (-> @state/jvm-main-db :connection :url))]
        (and (-> @data* :entity-id presence boolean)
             (= :set (-> @data* :entity-type))
             (= (:domain @data*) (.getDomain connection-url))
             (= (:port @data*) (.getPort connection-url))
             (= (:schme @data*) (.getScheme connection-url)))))))

(def target-dir-valid?*
  (reaction (-> @form-data* :target-directory presence boolean)))

(def form-valid?*
  (reaction (and @url-valid?* @target-dir-valid?*)))

(def set-value
  (form-utils/create-update-form-data-setter
    state/client-db
    [:download :pre-check-form]))

(defn initialize-form-data []
  (set-value :target-directory
             (-> @state/jvm-main-db :download :target-directory)))

(defn submit []
  (let [req {:method :post
             :json-params @data*
             :path "/download/step1"}]
    (request/send-off
      req {:title "Export/Download Step1!"})))

(defn url-input-component []
  [:div.form-group
   {:class (when-not @url-valid?* "has-error")}
   [:label {:for "url"} "URL of the set to be exported:"]
   [:input.form-control {:type "url"
                         :placeholder "https://your-madek-server/sets/UUID"
                         :value (:url @form-data*)
                         :on-change #(set-value
                                       :url (-> % .-target .-value presence))}]
   [:p.help-block
    "The host and protocol must be equal to those from the connection. "]
   [:p.help-block
    "Only sets can be downloaded and the url must be given in the UUID form. "
    "Copy the first link of the " [:strong [:em "custom urls"]] " in the Madek web interface."]])

(defn target-dir-input-component []
  [:div.form-group
   {:class (when-not @target-dir-valid?* "has-error")}
   [:label {:for :target-directory} "Target directory:"]
   [:input.form-control {:type :text
                         :value (:target-directory @form-data*)
                         :on-change #(set-value
                                       :target-directory (-> % .-target .-value presence))}]
   [:p.help-block
    "The exported/downloaded data and files will be stored in this directory. "]])

(defn debug-component []
  [:div.debug
   (when (:debug @state/client-db)
     [:div.debug
      [:h3 "Debug Step-1"]
      [:section.data
       [:h4 "form-data*"]
       [:pre (with-out-str (pprint @form-data*))]]
      [:section.data
       [:h4 "data*"]
       [:pre (with-out-str (pprint @data*))]]])])

(defn component []
  (reagent/create-class
    {:component-will-mount initialize-form-data
     :render (fn []
               [:section.pre-download-check
                [:h2 "Step 1 - Choose Entity and Target Directory"]
                [:section.form
                 [url-input-component]
                 [target-dir-input-component]
                 [:div.clearfix
                  [:button.btn.btn-primary.pull-right
                   (merge
                     {:on-click submit}
                     (when (not @form-valid?*) {:disabled true}))
                   "Next"]] [:div.clearfix]]
                [debug-component]])}))

