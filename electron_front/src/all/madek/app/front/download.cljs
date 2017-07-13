(ns madek.app.front.download
  (:refer-clojure :exclude [str keyword])
  (:require
    [madek.app.front.download.step1 :as step1]
    [madek.app.front.download.step2 :as step2]
    [madek.app.front.download.step3 :as step3]
    [madek.app.front.download.download :as download]


    [madek.app.front.request :as request]
    [madek.app.front.utils :refer [str keyword deep-merge presence]]
    [madek.app.front.utils.form :as form-utils]

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


(def download* (reaction (-> @state/jvm-main-db :download)))

(defn not-found []
  [:div
   [:h2.text-danger "Bug: The Page for the State "
    [:code (with-out-str (pprint (:state @download*))) ]
    " is not implemented." ]])

(defn debug-component []
  [:div.debug
   (when (:debug @state/client-db)
     [:div
      [:hr]
      [:h3 "Debug Export Page"]
      [:section.download
       [:h3 "@download*"]
       [:pre (with-out-str (pprint @download*))]]
      [:hr]])])

(defn page []
  [:div.page
   [:h1 "Export"]
   (cond
     (-> @download* :download-finished) [download/downloaded-component]
     (-> @download* :download-started) [download/downloading-component]
     (-> @download* :step2-completed) [step3/component]
     (-> @download* :step1-completed) [step2/component]
     :else [step1/component])
   [debug-component]])
