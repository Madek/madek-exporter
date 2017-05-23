(ns madek.app.front.request
  (:refer-clojure :exclude [str keyword send-off])
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    [cljs.core.async.macros :refer [go]]
    )
  (:require
    [madek.app.front.utils :refer [str keyword deep-merge]]
    [madek.app.front.state :as state]

    [cljs-http.client :as http]
    [cljs-uuid-utils.core :as uuid]
    [fipp.edn :refer [pprint]]
    [goog.string :as gstring]
    [goog.string.format]
    [reagent.core :as r]
    ))

(def META-DEFAULTS
  {:show_request_modal true
   :show_response_success_modal false
   :show_response_error_modal true })

(defn send-off [req-opts meta-req & {:keys [callback]
                                     :or {callback nil}}]
  (let [req (deep-merge {:method :post
                         :headers {"accept" "application/json-roa+json"}
                         :url (str "http://localhost:"
                                   (-> @state/electron-main-db :jvm-process :port)
                                   (:path req-opts))}
                        req-opts)
        id (uuid/uuid-string (uuid/make-random-uuid))]
    (swap! state/client-db assoc-in [:requests id]
           {:request req :meta (deep-merge META-DEFAULTS meta-req)})
    (go (let [resp (<! (http/request req))]
          (when (-> @state/client-db :requests (get id))
            (swap! state/client-db assoc-in [:requests id :response] resp))
          (when callback (callback resp))
          ; TODO dismiss it after 3 minutes or so when success
          ))
    id))

(defn response-pending? [request]
  (empty? (:response request)))

(defn response-success? [resp]
  (<= 200 (-> resp :status) 299))

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
           [:h4 (str " Request "
                     (when-let [title (-> request :meta :title)]
                       (str " \"" title "\" "))
                     (case bootstrap-status
                       :danger " ERROR "
                       nil))
            (-> request :response :status)]]
          [:div.modal-body
           (case bootstrap-status
             :success [:p (-> request :response :body)]
             :pending [:p "Please stand by!"]
             :danger (if-let [body (-> request :response :body)]
                       body
                       [:pre (with-out-str (pprint request))]))]
          [:div.modal-footer
           [:div.clearfix]
           [:button.btn
            {:class (str "btn-" bootstrap-status)
             :on-click #(swap! state/client-db
                               update-in [:requests]
                               (fn [rx] (dissoc rx (:id request))))}
            "Dismiss"]
           ]]]]
       [:div.modal-backdrop {:style {:opacity "0.2"}}]])))

