(ns madek.app.front.connection
  (:refer-clojure :exclude [str keyword])
  (:require
    [madek.app.front.utils :refer [str keyword deep-merge presence]]
    [madek.app.front.i18n :as i18n]
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

(defn connect-params []
  (let [fd @form-data
        base (select-keys fd [:url :sign-in-method :show-password])]
    (case (or (:sign-in-method fd) :token)
      :login (merge base (select-keys fd [:login :password]))
      :token (merge base
                    (when-let [token (presence (:password fd))]
                      {:api-token token}))
      base)))

(defn connect []
  (let [req {:method :post
             :json-params (connect-params)
             :path "/connect"}]
    (request/send-off
      req {:title (i18n/t :connection/connect-req)
           :error-title (i18n/t :connection/connect-failed)})))


;;; disconnect ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn clear-form-sign-in-data []
  (update-form-data-value :login nil)
  (update-form-data-value :password nil))

(defn disconnect []
  (let [req {:method :delete
             :path "/connect"}]
    (request/send-off
      req {:title (i18n/t :connection/disconnect-req)}
      :callback (fn [_]
                  (clear-form-sign-in-data)
                  (accountant/navigate! "/connection")))))


;;; form ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn base-url-form-component []
  [:div.form-group {:class (if @url-is-valid "" "has-error")}
   [:label {:for "url"} (i18n/t :connection/base-url)]
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
     [:label {:for "login"} (i18n/t :connection/login-label)]
     [:input.login.form-control
      {:type "text"
       :placeholder (i18n/t :connection/login-placeholder)
       :value (:login @form-data)
       :on-change #(update-form-data-value
                     :login (-> % .-target .-value presence))}]]))

(defn token-form-component []
  (let [show-password? (-> @form-data :show-password)
        toggle-label (if show-password?
                       (i18n/t :connection/hide-password)
                       (i18n/t :connection/show-password))]
    [:div.form-group
     [:label {:for "password"}
      (case @sign-in-method*
        :token (i18n/t :connection/api-token)
        :login (i18n/t :connection/password)
        (i18n/t :connection/oops))]
     [:div.input-group
      [:input.password.form-control
       {:type (if show-password? "text" "password")
        :id "password"
        :placeholder (i18n/t :connection/password-placeholder)
        :value (:password @form-data)
        :on-change #(update-form-data-value
                      :password (-> % .-target .-value presence))}]
      [:span.input-group-btn
       [:button.btn.btn-default
        {:type "button"
         :title toggle-label
         :aria-label toggle-label
         :on-click #(update-form-data
                      (fn [fd] (assoc fd :show-password (not (:show-password fd)))))}
        [:span.glyphicon
         {:class (if show-password?
                   "glyphicon-eye-close"
                   "glyphicon-eye-open")
          :aria-hidden "true"}]]]]]))


(defn connect-form []
  [:div.form
   [base-url-form-component]
   [:ul.nav.nav-tabs {:style {:margin-bottom "1em"}}
    [:li {:class (when (= @sign-in-method* :token) "active")}
     [:a {:href "#"
          :on-click (fn [_]
                      (clear-form-sign-in-data)
                      (update-form-data-value :sign-in-method :token))}
      (i18n/t :connection/sign-in-token)]]
    [:li {:class (when (= @sign-in-method* :login) "active")}
     [:a {:href "#"
          :on-click (fn [_]
                      (clear-form-sign-in-data)
                      (update-form-data-value :sign-in-method :login))}
      (i18n/t :connection/sign-in-login)]]]
   [login-form-component]
   [token-form-component]
   [:div.form-group.pull-right
    [:button.btn.btn-primary
     (merge {:on-click connect}
            (when (not @form-is-valid)
              {:disabled "yes"}))
     (i18n/t :connection/connect)]]])

(defn continue-form []
  [:div.form
   [:div.pull-left
    [:button.btn.btn-warning
     {:on-click disconnect}
     (i18n/t :connection/disconnect) ]]
   [:div.pull-right
    [:a.btn.btn-primary
     {:href "/download"}
     (i18n/t :connection/continue-export) ]]])

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
     [:h2 (i18n/t :connection/connected)]]
    [:div.panel.panel-body
     [:p.text-success
      (i18n/t :connection/connected-to-prefix) [:code (-> @connection* :url)]
      (i18n/t :connection/connected-as) [:code [:em (-> @connection* :auth-info :type)]] " "
      [:em (or (-> @connection* :auth-info :email_address presence)
               (-> @connection* :auth-info :login presence))] "."]]]])

(defn status-icon-component []
  (let [connected? @connected?*]
    [:li.connection-status
     [:a {:href "#"
          :title (if connected?
                   (i18n/t :connection/disconnect-hint)
                   (i18n/t :connection/not-connected))
          :on-click (fn [e]
                      (.preventDefault e)
                      (if connected?
                        (disconnect)
                        (accountant/navigate! "/connection")))}
      [:span.glyphicon.glyphicon-link
       {:class (if connected? "text-success" "text-danger")
        :aria-hidden "true"}]]]))

(defn connection-status-component []
  [:div.connection.status
   (when @connected?*
     [connection-connected-component])])

(defn debug-component []
  (when (:debug @state/client-db)
    [:div.debug
     [:hr]
     [:h3 (i18n/t :debug/title)]
     [:section.data
      [:h4 (i18n/t :connection/title)]
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
   [:h3 (i18n/t :connection/title)]
   [connection-status-component]
   (if-not @connected?*
     [connect-form]
     [continue-form])
   [:div.clearfix]
   [debug-component]])
