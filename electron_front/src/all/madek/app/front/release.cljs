(ns madek.app.front.release
  (:refer-clojure :exclude [str keyword])
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    [cljs.core.async.macros :refer [go]]
    )
  (:require
    [madek.app.front.utils :refer [str keyword deep-merge]]
    [madek.app.front.request :as request]
    [madek.app.front.state :as state]

    [cljs-http.client :as http]
    [cljs-uuid-utils.core :as uuid]
    [fipp.edn :refer [pprint]]
    [goog.string :as gstring]
    [goog.string.format]
    [reagent.core :as r]
    [cljs.nodejs :as nodejs]
    ))


(def Electron (nodejs/require "electron"))

(def shell (.-shell Electron))

(defn fetch-gh-release []
  (let [req-opts {:method :get
                  :url "https://api.github.com/repos/madek/madek-exporter/releases/latest"
                  :headers { "accept" "application/vnd.github.v3+json" }}
        meta-opts {:title "Fetch GH latest release info."
                   :show_request_modal false
                   :show_response_error_modal false}]
    (request/send-off
      req-opts meta-opts
      :callback (fn [response]
                  (js/setTimeout #(fetch-gh-release)
                                 (* 15 60 1000))
                  (when (:success response)
                    (when-not (-> response :body :draft)
                      (swap! state/client-db
                             assoc-in [:github :latest-release] (:body response))))))))

(js/setTimeout
  #(fetch-gh-release)
  (* 30 1000))

(def version*
  (reaction
    (when-let[release (-> @state/electron-main-db :environment :latest-release)]
      (str (:version_major release)
           "." (:version_minor release)
           "." (:version_patch release)
           ))))

(def update-available?
  (reaction
    (if-let [latest-gh-release (-> @state/client-db :github :latest-release)]
      (if (and @version*
               (not= @version* (:tag_name latest-gh-release)))
        true
        false)
      false)))

(defn update-available-alert-component []
  (when @update-available?
    [:div.alert.alert-warning
     [:h3 "An update of the Madek Exporter is available"]
     [:p "The latest published release on the download site has version "
      [:code (-> @state/client-db :github :latest-release :tag_name)]
      " but the version of this instance is " [:code @version*] "."]
     [:p "You can download the current release from "
      [:a {:href "https://github.com/Madek/madek-exporter/releases"
           :on-click (fn [e]
                       (.preventDefault e)
                       (.openExternal
                         shell
                         "https://github.com/Madek/madek-exporter/releases"))
           :style {:font-family "monospaced"}}
       "https://github.com/Madek/madek-exporter/releases"
       ]"."]]))

(defn release-info-component []
  [:div.release-info
   (if-let [latest-gh-release (-> @state/client-db :github :latest-release)]
     (when (= @version* (:tag_name latest-gh-release))
       [:div.alert.alert-success
        [:p [:strong "You are up to date."]
         " Version "
         [:code @version*]
         " is the last version published on the download site."]])
     [:div
      [:div.alert.alert-info
       [:p "There is currently no release information from the download site available!"]
       ]])])
