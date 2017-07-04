(ns madek.app.front.connection
  (:refer-clojure :exclude [str keyword])
  (:require
    [madek.app.front.utils :refer [str keyword deep-merge presence]]
    [madek.app.front.request :as request]

    [fipp.edn :refer [pprint]]
    [reagent.ratom :as ratom :refer [reaction]]
    [madek.app.front.state :as state]
    [madek.app.front.env]
    [cljs.nodejs :as nodejs]
    )
  (:import
    [goog Uri]
    )
  )


(def Electron (nodejs/require "electron"))

(def form-data (reaction (-> @state/client-db :connection :form)))

(def connected-entity*
  (reaction
    (if-let [email-address (-> @state/jvm-main-db :connection :email_address)]
      (-> email-address (clojure.string/split #"@") first)
      (-> @state/jvm-main-db :connection :login))))

(def connected-target*
  (reaction
    (when-let [url (-> @state/jvm-main-db :connection :url)]
      (let [gurl (Uri. url)]
        (str (.getDomain gurl)
             (when-let [p (.getPort gurl)]
               (str ":" p)))))))

(defn compact-component []
  [:span
   (when-let [ce @connected-entity*]
     [:span ce])
   (when-let [ct @connected-target*]
     [:span
      " " ; utf-8 m-space!
      [:span ct ]])])

(defn connect []
  (let [req {:method :post
             :json-params @form-data
             :path "/connect"}]
    (request/send-off
      req {:title "Connect!"})))

(defn disconnect []
  (let [req {:method :delete
             :path "/connect"}]
    (request/send-off
      req {:title "Disconnect!"})))

(defn update-form-data [fun]
  (swap! state/client-db
         (fn [cs]
           (assoc-in cs [:connection :form]
                     (fun (-> cs :connection :form))))))

(defn update-form-data-value [k v]
  (update-form-data (fn [fd] (assoc fd k v))))


(def url-is-valid
  (reaction
    (boolean
      (when-let [url (-> @form-data :url presence)]
        (re-matches #"https?://[^/]+" url)))))

(def form-is-valid url-is-valid)

(def show-password* (atom false))

(defn connect-form []
  [:div.form
   [:div.form-group {:class (if @url-is-valid "" "has-error")}
    [:label {:for "url"} "Madek base URL "]
    [:input.url.form-control
     {:class (if @url-is-valid "" "has-error")
      :type "url"
      :placeholder "https://medienarchiv.zhdk.ch"
      :value (:url @form-data)
      :on-change #(update-form-data-value
                    :url (-> % .-target .-value presence))}]]
   [:div.form-group
    [:label {:for "login"} "Login | e-mail address | api-client name"]
    [:input.login.form-control
     {:type "text"
      :placeholder "Login"
      :value (:login @form-data)
      :on-change #(update-form-data-value
                    :login (-> % .-target .-value presence))}]]

   [:div.form-group
    [:label {:for "password"} "Password | api-token"
     [:span " ("
      [:input {:type :checkbox
               :on-change #(update-form-data (fn [fd] (assoc fd :show-password (-> fd :show-password not)))) ;#(swap! show-password* (fn [sp] (not sp)))
               :checked (-> @form-data :show-password)}] " show)"]]
    [:input.password.form-control
     {:type (if (-> @form-data :show-password) "text" "password")
      :placeholder "Password"
      :value (:password @form-data)
      :on-change #(update-form-data-value
                    :password(-> % .-target .-value presence))}]]
   [:div.form-group.pull-right
    [:button.btn.btn-primary
     (merge {:on-click connect}
            (when (not @form-is-valid)
              {:disabled "yes"}))
     "Connect"]]])

(defn continue-form []
  [:div.form
   [:div.pull-left
    [:button.btn.btn-warning
     {:on-click disconnect}
     "Disconnect" ]]
   [:div.pull-right
    [:a.btn.btn-primary
     {:href "/download"}
     "Continue to export" ]]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def connection* (reaction (-> @state/jvm-main-db :connection)))

(def connected?*
  (reaction (and (-> @connection* :url presence boolean)
                 (-> @connection* :email_address presence boolean))))

(defn connection-connected-component []
  [:div.connected
   [:div.panel.panel-success
    [:div.panel.panel-heading
     [:h2 "Connected!"]]
    [:div.panel.panel-body
     [:p.text-success
      "Your are connected to " [:code (-> @connection* :url)]
      " as " [:code [:em (-> @connection* :email_address)]] "."]
     [:pre (with-out-str (pprint @connection*))]]]])

(defn connection-pending-component []
  [:div.pending
   [:div.alert.alert-warning
    [:p "You are not connected yet!"
     ]]])

(defn connection-status-component []
  [:div.connection.status
   (if @connected?*
     [connection-connected-component]
     [connection-pending-component])
   ])


(defn page []
  [:div.connection
   [:h1 "Connection"]
   [connection-status-component]
   (if-not @connected?*
     [connect-form]
     [continue-form]
     )])
