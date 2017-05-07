(ns madek.app.front.connection
  (:refer-clojure :exclude [str keyword])
  (:require
    [madek.app.front.utils :refer [str keyword deep-merge presence]]

    [fipp.edn :refer [pprint]]
    [reagent.ratom :as ratom :refer [reaction]]
    [madek.app.front.state :as state]
    [madek.app.front.env]
    [cljs.nodejs :as nodejs]
    ))


(def Electron (nodejs/require "electron"))

(defn connect []
  (js/console.log "CONNECT!")
  ; TODO
  ;(.send (.-ipcRenderer Electron) "madek:connect" (-> @state/client-db :connection :form clj->js))
  )

(defn update-form-data [fun]
  (swap! state/client-db
         (fn [cs]
           (assoc-in cs [:connection :form]
                     (fun (-> cs :connection :form))))))

(defn update-form-data-value [k v]
  (update-form-data (fn [fd] (assoc fd k v))))

(def form-data (reaction (-> @state/client-db :connection :form)))

(def url-is-valid
  (reaction
    (boolean
      (when-let [url (-> @form-data :url presence)]
        (re-matches #"https?://[^/]+" url)))))

(def form-is-valid url-is-valid)

(defn form []
  [:div.form
   [:div.form-group {:class (if @url-is-valid "" "has-error")}
    [:label {:for "url"} "Madek base URL "]
    [:input.url.form-control
     {:class (if @url-is-valid "" "has-error")
      :type "url"
      :placeholder "URL of you madek instance"
      :value (:url @form-data)
      :on-change #(update-form-data-value
                    :url (-> % .-target .-value))}]]
   [:div.form-group
    [:label {:for "login"} "Login | e-mail address | api-client name"]
    [:input.login.form-control
     {:type "text"
      :placeholder "Login"
      :value (:login @form-data)
      :on-change #(update-form-data-value
                    :login (-> % .-target .-value))}]]
   [:div.form-group
    [:label {:for "password"} "Password | token"]
    [:input.password.form-control
     {:type "password"
      :placeholder "Password"
      :value (:password @form-data)
      :on-change #(update-form-data-value
                    :password(-> % .-target .-value))
      }]]
   [:div.form-group.pull-right
    [:button.btn.btn-primary
     (merge {:on-click #(connect)}
            (when (not @form-is-valid)
              {:disabled "yes"}))
     "Connect"]]])


(defn page []
  [:div.connection
   [:h1 "Connection"]
   (form)])
