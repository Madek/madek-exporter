(ns madek.app.front.request
  (:refer-clojure :exclude [str keyword send-off])
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    [cljs.core.async.macros :refer [go]]
    )
  (:require
    [madek.app.front.utils :refer [str keyword deep-merge presence]]
    [madek.app.front.i18n :as i18n]
    [madek.app.front.state :as state]

    [cljs-http.client :as http]
    [cljs-uuid-utils.core :as uuid]
    [cljs.core.async :refer [timeout]]
    [goog.string :as gstring]
    [goog.string.format]
    [reagent.core :as r]
    ))

(def META-DEFAULTS
  {:show_request_modal true
   :show_response_success_modal false
   :show_response_error_modal true
   :autoremove-delay 1000
   :autoremove-on-success true})

;;; autoremove ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn response-success? [resp]
  (<= 200 (-> resp :status) 299))

(defn response-error-message [response]
  (let [body (:body response)]
    (or (when (map? body)
          (or (:message body)
              (get body "message")))
        (when (= 0 (:status response))
          (i18n/t :request/service-unreachable))
        (i18n/t :request/could-not-complete))))

(defn autoremove [id meta]
  (go (<! (timeout 30000))
      (swap! state/client-db update :requests
             (fn [rqs] (dissoc rqs id)))))


;;; send-off ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn send-off [req-opts meta-req & {:keys [callback]
                                     :or {callback nil}}]
  (let [req (deep-merge {:method :post
                         :headers {"accept" "application/json-roa+json"}
                         :url (str "http://localhost:"
                                   (-> @state/electron-main-db :jvm-process :port)
                                   (:path req-opts))}
                        req-opts)
        id (uuid/uuid-string (uuid/make-random-uuid))
        params (:json-params req)
        params-for-log (cond-> (dissoc params :password :api-token)
                         (contains? params :password)
                         (assoc :password "***redacted***"
                                :password-present (boolean (presence (:password params))))
                         (contains? params :api-token)
                         (assoc :api-token "***redacted***"
                                :api-token-present (boolean (presence (:api-token params)))))]
    (state/add-app-log!
      :info :request/send
      {:id id
       :method (:method req)
       :url (:url req)
       :path (:path req-opts)
       :json-params params-for-log})
    (swap! state/client-db assoc-in [:requests id]
           {:request req :meta (deep-merge META-DEFAULTS meta-req)})
    (go (let [resp (<! (http/request req))]
          (when (-> @state/client-db :requests (get id))
            (swap! state/client-db assoc-in [:requests id :response] resp))
          (state/add-app-log!
            (if (response-success? resp) :info :error)
            :request/response
            {:id id
             :status (:status resp)
             :success (:success resp)
             :error-code (:error-code resp)
             :path (:path req-opts)
             :hint (when (= 0 (:status resp))
                     "Local JVM endpoint not reachable. Check if jvm-main is running and listening on localhost.")
             :body (when-not (response-success? resp)
                     (or (:body resp)
                         (:error-text resp)))})
          (when (and (response-success? resp)
                     (:autoremove-on-success meta-req))
            (autoremove id meta-req))
          (when callback (callback resp))))
    id))

(defn response-pending? [request]
  (empty? (:response request)))

(defn show-modal? [request]
  (if (response-pending? request)
    (-> request :meta :show_request_modal)
    (if (response-success? (-> request :response))
      (-> request :meta :show_response_success_modal)
      (-> request :meta :show_response_error_modal))))

(def current-modal-request
  (do
    (reaction
      (->> @state/client-db :requests
           (map (fn [[id v]] (assoc v :id id)))
           (filter show-modal?)
           first))))

(defn modal []
  (when-let [request @current-modal-request]
    (let [bootstrap-status (cond (response-pending? request) :pending
                                 (-> request :response :success) :success
                                 :else :danger)]
      [:div
       [:div.modal {:style {:display "block"}}
        [:div.modal-dialog
         [:div.modal-content {:class (str "modal-" bootstrap-status)}
          [:div.modal-header
           [:h4 (if (= bootstrap-status :danger)
                  (or (-> request :meta :error-title)
                      (i18n/t :request/failed))
                  (str (i18n/t :request/pending-title)
                       (when-let [title (-> request :meta :title)]
                         (str " \"" title "\" "))
                       (-> request :response :status)))]]
          [:div.modal-body
           (case bootstrap-status
             :success [:p (-> request :response :body)]
             :pending [:p (i18n/t :request/stand-by)]
             :danger [:div.alert.alert-danger
                      [:p (response-error-message (:response request))]])]
          [:div.modal-footer
           [:div.clearfix]
           [:button.btn
            {:class (str "btn-" bootstrap-status)
             :on-click #(swap! state/client-db
                               update-in [:requests]
                               (fn [rx] (dissoc rx (:id request))))}
            (i18n/t :request/dismiss)]
           ]]]]
       [:div.modal-backdrop {:style {:opacity "0.2"}}]])))

