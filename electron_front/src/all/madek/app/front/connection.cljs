(ns madek.app.front.connection
  (:require
    [fipp.edn :refer [pprint]]
    [reagent.ratom :as ratom :refer [reaction]]
    [madek.app.front.state :as state]
    [madek.app.front.env]
    [cljs.nodejs :as nodejs]
    ))


(def Electron (nodejs/require "electron"))

(defn connect []
  (js/console.log "CONNECT!")
  (.send (.-ipcRenderer Electron) "madek:connect"
         (-> @state/client-db :connection :form clj->js)))

(defn update-form-data [fun]
  (swap! state/client-db
         (fn [cs]
           (assoc-in cs [:connection :form]
                     (fun (-> cs :connection :form))))))

(defn update-form-data-value [k v]
  (update-form-data (fn [fd] (assoc fd k v))))

(def form-data (reaction (-> @state/client-db :connection :form)))

(defn form []
  [:div.form
   [:div.form-group
    [:label {:for "url"} "URL "]
    [:input#url
     {:type "text"
      :placeholder "URL of you madek instance"
      :value (:url @form-data)
      :on-change #(update-form-data-value
                    :url (-> % .-target .-value))
      }]]
   [:div.form-group
    [:label {:for "login"} "Login / e-mail address / api-client name"]
    [:input#url
     {:type "text"
      :placeholder "Login"
      :value (:login @form-data)
      :on-change #(update-form-data-value
                    :login (-> % .-target .-value))}]]
   [:div.form-group
    [:label {:for "password"} "Password / token"]
    [:input#url
     {:type "password"
      :placeholder "Password"
      :value (:password @form-data)
      :on-change #(update-form-data-value
                    :password(-> % .-target .-value))
      }]]
   [:button {:on-click #(connect)}
    "Connect"]])


(defn page []
  [:div.connection
   [:h1 "Connection"]
   (form)])
