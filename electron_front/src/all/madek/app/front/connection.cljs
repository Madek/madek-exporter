(ns madek.app.front.connection
  (:refer-clojure :exclude [str keyword])
  (:require
    [madek.app.front.breadcrumb :as breadcrumb]
    [madek.app.front.bridge :as bridge]
    [madek.app.front.help :as help]
    [madek.app.front.utils :refer [str keyword deep-merge presence]]
    [madek.app.front.i18n :as i18n]
    [madek.app.front.request :as request]

    [accountant.core :as accountant]
    [fipp.edn :refer [pprint]]
    [reagent.core :as reagent]
    [reagent.ratom :as ratom :refer [reaction]]
    [madek.app.front.state :as state]
    [madek.app.front.env]
    )
  (:import
    [goog Uri]
    )
  )

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

(def credentials-are-valid
  (reaction
    (case (or (:sign-in-method @form-data) :token)
      :login (boolean (and (presence (:login @form-data))
                           (presence (:password @form-data))))
      :token (boolean (presence (:password @form-data)))
      false)))

(def form-is-valid
  (reaction (and @url-is-valid @credentials-are-valid)))

(def show-password* (atom false))

(def sign-in-method*
  (reaction (or (:sign-in-method @form-data)
                :token)))


;;; connect ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn clear-form-sign-in-data []
  (update-form-data-value :login nil)
  (update-form-data-value :password nil))

(defn connect-params []
  (let [fd @form-data
        ;; Prefer the picker value; first connect defaults to api.
        api-mode (name (keyword (or (:api-mode fd)
                                    (-> @state/jvm-main-db :connection :api-mode)
                                    :api)))
        base (merge (select-keys fd [:url :sign-in-method :show-password])
                    {:api-mode api-mode})]
    (case (or (:sign-in-method fd) :token)
      :login (merge base (select-keys fd [:login :password]))
      :token (merge base
                    (when-let [token (presence (:password fd))]
                      {:api-token token}))
      base)))

(defn- connect!
  "POST /connect. `opts` may include :title, :error-title, :callback."
  ([] (connect! {}))
  ([{:keys [title error-title callback]}]
   (let [req {:method :post
              :json-params (connect-params)
              :path "/connect"}]
     (request/send-off
       req {:title (or title (i18n/t :connection/connect-req))
            :error-title (or error-title (i18n/t :connection/connect-failed))}
       :callback (fn [resp]
                   (when (request/response-success? resp)
                     (clear-form-sign-in-data))
                   (when callback (callback resp)))))))

(defn connect
  "Connect button handler. Ignores the DOM click event (must stay 0-arity
  for `:on-click connect`; multi-arity would treat the event as opts)."
  []
  (connect!))

(def connection* (reaction (-> @state/jvm-main-db :connection)))

(def connected?*
  (reaction (and (-> @connection* :url presence boolean)
                 (or (-> @connection* :email_address presence boolean)
                     (-> @connection* :login presence boolean)))))

(defn- selected-api-mode []
  (keyword
   (or (:api-mode @form-data)
       (:api-mode @connection*)
       :api)))

(defn- current-api-status []
  (:api-status @connection*))

(defn- api-probe-available? [probe]
  (boolean (or (:available probe) (get probe "available"))))

(defn- api-mode-available? [api-status mode]
  (if-not api-status
    false
    (let [mode (keyword (or mode :api))
          probe (fn [k]
                  (api-probe-available?
                   (or (get api-status k) (get api-status (name k)) {})))]
      (case mode
        :api (probe :api)
        :api-v2 (probe :api-v2)
        :both (and (probe :api) (probe :api-v2))
        false))))

(def selected-api-mode-available?*
  (reaction (api-mode-available? (current-api-status) (selected-api-mode))))

(def export-ready?*
  (reaction (and @connected?* @selected-api-mode-available?*)))

(defn set-api-mode!
  "Persist the picker choice on the existing JVM session (no re-auth)."
  [mode]
  (when (api-mode-available? (current-api-status) mode)
    (let [mode (keyword mode)
          previous (keyword (or (:api-mode @form-data)
                                (-> @state/jvm-main-db :connection :api-mode)
                                :api))]
      (when (not= mode previous)
        (update-form-data-value :api-mode mode)
        (request/send-off
         {:method :patch
          :path "/connect/api-mode"
          :json-params {:api-mode (name mode)}}
         {:title (i18n/t :connection/api-mode-update-req)
          :error-title (i18n/t :connection/api-mode-update-failed)}
         :callback (fn [resp]
                     (when-not (request/response-success? resp)
                       (update-form-data-value :api-mode previous))))))))


;;; disconnect ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn disconnect []
  (let [req {:method :delete
             :path "/connect"}]
    (request/send-off
      req {:title (i18n/t :connection/disconnect-req)}
      :callback (fn [_]
                  (clear-form-sign-in-data)
                  (accountant/navigate! "/connection")))))


;;; form ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def default-madek-url "https://medienarchiv.zhdk.ch")

(defn- preset-default-url! [e]
  (.preventDefault e)
  (update-form-data-value :url default-madek-url))

(defn- select-sign-in-method! [method]
  (update-form-data-value :sign-in-method method))

(defn base-url-form-component []
  [:div.form-group {:class (if @url-is-valid "" "has-error")}
   [:label {:for "url"
            :style {:cursor "pointer"}
            :title default-madek-url
            :on-click preset-default-url!}
    (i18n/t :connection/base-url)]
   [:input.url.form-control
    {:class (if @url-is-valid "" "has-error")
     :type "url"
     :placeholder default-madek-url
     :value (:url @form-data)
     :on-change #(update-form-data-value
                   :url (-> % .-target .-value presence))}]])

(defn- sign-in-info []
  (let [open?* (reagent/atom false)]
    (fn []
      (let [label (i18n/t :connection/sign-in-info)]
        [:span.sign-in-info
         {:on-mouse-enter #(reset! open?* true)
          :on-mouse-leave #(reset! open?* false)}
         [:span.glyphicon.glyphicon-info-sign
          {:tab-index 0
           :role "button"
           :aria-label label
           :aria-expanded @open?*
           :on-focus #(reset! open?* true)
           :on-blur #(reset! open?* false)}]
         (when @open?*
           [:div.sign-in-info-panel
            [:ul.list-unstyled
             [:li
              [:a {:href "/help"
                   :on-mouse-down #(.preventDefault %)
                   :on-click (fn [e]
                               (.preventDefault e)
                               (reset! help/scroll-to* help/login-variants-id)
                               (accountant/navigate! "/help"))}
               (str (i18n/t :help/title) " / " (i18n/t :help/login-variants))]]
             [:li
              (let [url (help/tokens-url)]
                [:a {:href url
                     :on-mouse-down #(.preventDefault %)
                     :on-click (fn [e]
                                 (.preventDefault e)
                                 (bridge/open-external url))}
                 (i18n/t :connection/tokens-overview)])]]])]))))

(defn login-form-component []
  (when (= @sign-in-method* :login)
    [:div.form-group
     [:label {:for "login"} (i18n/t :connection/login-label) [sign-in-info]]
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
        (i18n/t :connection/oops))
      (when (= @sign-in-method* :token) [sign-in-info])]
     [:div.input-group
      [:input.password.form-control
       {:type (if show-password? "text" "password")
        :id "password"
        :placeholder (case @sign-in-method*
                       :token (i18n/t :connection/token-placeholder)
                       (i18n/t :connection/password-placeholder))
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


(defn- connect-form-view []
  [:div.form
   [base-url-form-component]
   [:ul.nav.nav-tabs {:style {:margin-bottom "1em"}}
    [:li {:class (when (= @sign-in-method* :token) "active")}
     [:a {:href "#"
          :on-click (fn [e]
                      (.preventDefault e)
                      (select-sign-in-method! :token))}
      (i18n/t :connection/sign-in-token)]]
    [:li {:class (when (= @sign-in-method* :login) "active")}
     [:a {:href "#"
          :on-click (fn [e]
                      (.preventDefault e)
                      (select-sign-in-method! :login))}
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
  (let [export-ready? @export-ready?*]
    [:div.form
     [:div.pull-left
      [:button.btn.btn-warning
       {:on-click disconnect}
       (i18n/t :connection/disconnect)]]
     [:div.pull-right
      [:a.btn.btn-primary
       (merge {:href "/download"
               :on-click (fn [e]
                           (when-not export-ready?
                             (.preventDefault e)))}
              (when-not export-ready?
                {:class "disabled"
                 :aria-disabled "true"
                 :tabIndex -1
                 :title (i18n/t :connection/api-mode-unavailable-warn)}))
       (i18n/t :connection/continue-export)]]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn can-access-download? []
  (let [download (-> @state/jvm-main-db :download)]
    (or (:download-started download)
        (:download-finished download)
        @export-ready?*)))

(defn- api-probe-status-icon [available?]
  [:span.glyphicon
   {:class (if available?
             "glyphicon-ok text-success"
             "glyphicon-remove text-danger")
    :aria-hidden "true"}])

(defn- api-probe-status-row [api-status]
  [:div.api-probe-status
   (doall
    (for [[label k] [["/api" :api] ["/api-v2" :api-v2]]]
      (let [probe (or (get api-status k)
                      (get api-status (name k))
                      {})
            available? (api-probe-available? probe)
            status-label (i18n/t (if available?
                                   :connection/api-available
                                   :connection/api-unavailable))]
        ^{:key label}
        [:span.api-probe-status-item
         {:title (str label " — " status-label)}
         [:code label]
         [api-probe-status-icon available?]
         [:span.sr-only (str label " " status-label)]])))])

(defonce api-mode-dropdown-open* (reagent/atom false))

(defn- api-mode-option-label [mode]
  (i18n/t (keyword (str "connection/api-mode-" (name mode)))))

(defn- close-api-mode-dropdown-on-outside-click [e]
  (when @api-mode-dropdown-open*
    (when-let [el (.querySelector js/document ".api-mode-picker")]
      (when-not (.contains el (.-target e))
        (reset! api-mode-dropdown-open* false)))))

(defn- close-api-mode-dropdown-on-escape [e]
  (when (= "Escape" (.-key e))
    (reset! api-mode-dropdown-open* false)))

(defn- api-mode-dropdown-item [mode api-mode mode-available?]
  (let [available? (mode-available? mode)
        selected? (= mode api-mode)
        status-label (i18n/t (if available?
                               :connection/api-available
                               :connection/api-unavailable))]
    ^{:key (name mode)}
    [:li {:class (cond
                   (not available?) "disabled"
                   selected? "active")}
     [:a {:href "#"
          :role "option"
          :aria-selected selected?
          :title status-label
          :tabIndex (if available? 0 -1)
          :on-click (fn [e]
                      (.preventDefault e)
                      (when available?
                        (reset! api-mode-dropdown-open* false)
                        (set-api-mode! mode)))}
      [api-probe-status-icon available?]
      [:span.api-mode-dropdown-label (api-mode-option-label mode)]
      [:span.sr-only (str (api-mode-option-label mode) " " status-label)]]]))

(defn- api-mode-picker-render [api-mode mode-available?]
  (let [open? @api-mode-dropdown-open*
        selected-available? (mode-available? api-mode)]
    [:div.api-mode-picker.dropdown {:class (when open? "open")}
     [:button#api-mode.btn.btn-default.form-control.dropdown-toggle
      {:type "button"
       :aria-haspopup "listbox"
       :aria-expanded (boolean open?)
       :on-click (fn [e]
                   (.stopPropagation e)
                   (swap! api-mode-dropdown-open* not))}
      [api-probe-status-icon selected-available?]
      [:span.api-mode-dropdown-label (api-mode-option-label api-mode)]
      [:span.caret]]
     [:ul.dropdown-menu {:role "listbox"}
      (doall
       (for [mode [:api :api-v2 :both]]
         [api-mode-dropdown-item mode api-mode mode-available?]))]]))

(def api-mode-picker-component
  (reagent/create-class
    {:component-did-mount
     (fn [_]
       (.addEventListener js/document "click" close-api-mode-dropdown-on-outside-click)
       (.addEventListener js/document "keydown" close-api-mode-dropdown-on-escape))
     :component-will-unmount
     (fn [_]
       (.removeEventListener js/document "click" close-api-mode-dropdown-on-outside-click)
       (.removeEventListener js/document "keydown" close-api-mode-dropdown-on-escape))
     :reagent-render
     (fn [api-mode mode-available?]
       [api-mode-picker-render api-mode mode-available?])}))

(defn api-mode-form-component []
  (when @connected?*
    (let [api-mode (selected-api-mode)
          api-status (current-api-status)
          mode-available? (partial api-mode-available? api-status)
          selected-available? (mode-available? api-mode)]
      [:div.panel.panel-default.api-mode-selection
       [:div.panel-heading
        [:h3.panel-title
         (i18n/t :connection/api-mode-label)]
        (when api-status
          [api-probe-status-row api-status])]
       [:div.panel-body
        (when (and api-status (not selected-available?))
          [:div.alert.alert-warning {:role "alert"}
           (i18n/t :connection/api-mode-unavailable-warn)])
        [:p.help-block {:style {:margin-top 0}}
         (i18n/t :connection/api-mode-help)]
        [:div.form-group.api-mode-control {:style {:margin-bottom 0}}
         [api-mode-picker-component api-mode mode-available?]]]])))

(defn connection-connected-component []
  (let [auth-type (-> @connection* :auth-info :type)
        user (or (-> @connection* :auth-info :email_address presence)
                 (-> @connection* :auth-info :login presence))]
    [:div.connected
     [:div.panel.panel-success
      [:div.panel-heading
       [:h2.panel-title (i18n/t :connection/connected)]]
      [:div.panel-body
       [:p.text-success
        (i18n/t :connection/connected-to-prefix) [:code (-> @connection* :url)]
        [:br]
        (i18n/t :connection/connected-as) [:code [:em auth-type]] " "
        [:em user] "."
        (when (= :login (some-> @connection* :sign-in-method keyword))
          (let [warn (i18n/t :connection/login-api-v2-warn)]
            [:span.login-api-v2-warn.text-warning
             {:title warn
              :style {:margin-left "0.5em" :cursor "help"}}
             [:span.glyphicon.glyphicon-warning-sign {:aria-hidden "true"}]
             [:span.sr-only warn]]))]]]]))

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
  [:div.page.connection
   [breadcrumb/page-breadcrumb (i18n/t :connection/title)]
   [connection-status-component]
   [api-mode-form-component]
   (if-not @connected?*
     [:div
      [:h2 (i18n/t :connection/login-title)]
      [connect-form-view]]
     [continue-form])
   [:div.clearfix]
   [debug-component]])
