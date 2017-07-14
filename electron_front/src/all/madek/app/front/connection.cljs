(ns madek.app.front.connection
  (:refer-clojure :exclude [str keyword])
  (:require
    [madek.app.front.utils :refer [str keyword deep-merge presence]]
    [madek.app.front.request :as request]

    [accountant.core :as accountant]
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
      email-address
      (-> @state/jvm-main-db :connection :login))))

(def connected-target*
  (reaction
    (when-let [url (-> @state/jvm-main-db :connection :url)]
      (let [gurl (Uri. url)]
        (str (.getDomain gurl)
             (when-let [p (.getPort gurl)]
               (str ":" p)))))))


;;; data ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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

(def sign-in-method*
  (reaction (or (:sign-in-method @form-data)
                :token)))


;;; connect ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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


;;; disconnect ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn clear-form-sign-in-data []
  (update-form-data-value :login nil)
  (update-form-data-value :password nil))

(defn disconnect []
  (let [req {:method :delete
             :path "/connect"}]
    (request/send-off
      req {:title "Disconnect!"}
      :callback (fn [_]
                  (clear-form-sign-in-data)
                  (accountant/navigate! "/connection")))))


;;; form ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn base-url-form-component []
  [:div.form-group {:class (if @url-is-valid "" "has-error")}
   [:label {:for "url"} "Madek base URL "]
   [:input.url.form-control
    {:class (if @url-is-valid "" "has-error")
     :type "url"
     :placeholder "https://medienarchiv.zhdk.ch"
     :value (:url @form-data)
     :on-change #(update-form-data-value
                   :url (-> % .-target .-value presence))}]])

(defn login-form-component []
  (when (= @sign-in-method* :login)
    [:div.form-group
     [:label {:for "login"} "Login | e-mail address | api-client name"]
     [:input.login.form-control
      {:type "text"
       :placeholder "Login"
       :value (:login @form-data)
       :on-change #(update-form-data-value
                     :login (-> % .-target .-value presence))}]]))

(defn token-form-component []
  [:div.form-group
   [:label {:for "password"}
    (case @sign-in-method*
      :token "Api-token"
      :login "Password"
      "Ooooops")
    [:span " ("
     [:input {:type :checkbox
              :on-change #(update-form-data (fn [fd] (assoc fd :show-password (-> fd :show-password not)))) ;#(swap! show-password* (fn [sp] (not sp)))
              :checked (-> @form-data :show-password)}] " show)"]]
   [:input.password.form-control
    {:type (if (-> @form-data :show-password) "text" "password")
     :placeholder "Password"
     :value (:password @form-data)
     :on-change #(update-form-data-value
                   :password(-> % .-target .-value presence))}]])


(defn connect-form []
  [:div.form
   [base-url-form-component]
   [:ul.nav.nav-tabs {:style {:margin-bottom "1em"}}
    [:li {:class (when (= @sign-in-method* :token) "active")}
     [:a {:href "#"
          :on-click (fn [_]
                      (clear-form-sign-in-data)
                      (update-form-data-value :sign-in-method :token))}
      "Sign in with token"]]
    [:li {:class (when (= @sign-in-method* :login) "active")}
     [:a {:href "#"
          :on-click (fn [_]
                      (clear-form-sign-in-data)
                      (update-form-data-value :sign-in-method :login))}
      "Sign in with login and password"]]]
   [login-form-component]
   [token-form-component]
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
                 (or (-> @connection* :email_address presence boolean)
                     (-> @connection* :login presence boolean)))))

(defn connection-connected-component []
  [:div.connected
   [:div.panel.panel-success
    [:div.panel.panel-heading
     [:h2 "Connected!"]]
    [:div.panel.panel-body
     [:p.text-success
      "Your are connected to " [:code (-> @connection* :url)]
      " as " [:code [:em (-> @connection* :auth-info :type)]] " "
      [:em (or (-> @connection* :auth-info :email_address presence)
               (-> @connection* :auth-info :login presence))] "."]]]])

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

(defn debug-component []
  (when (:debug @state/client-db)
    [:div.debug
     [:hr]
     [:h3 "Debug"]
     [:section.data
      [:h4 "Connection"]
      [:pre (with-out-str (pprint @connection*))]]
     [:section.data
      [:h4 "form-data"]
      [:pre (with-out-str (pprint @form-data))]]
     [:section.data
      [:h4 "sign-in-method*"]
      [:pre (with-out-str (pprint @sign-in-method*))]]
     ]))

(defn page []
  [:div.connection
   [:h1 "Connection"]
   [connection-status-component]
   (if-not @connected?*
     [connect-form]
     [continue-form])
   [:div.clearfix]
   [debug-component]
   ])
