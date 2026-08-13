(ns madek.app.front.download
  (:refer-clojure :exclude [str keyword])
  (:require
    [madek.app.front.breadcrumb :as breadcrumb]
    [madek.app.front.download.step1 :as step1]
    [madek.app.front.download.step2 :as step2]
    [madek.app.front.download.step3 :as step3]
    [madek.app.front.download.download :as download]
    [madek.app.front.i18n :as i18n]


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
   [:h2.text-danger (i18n/t :download/bug-state)
    [:code (with-out-str (pprint (:state @download*))) ]
    (i18n/t :download/bug-state-end)]])

(defn debug-component []
  [:div.debug
   (when (:debug @state/client-db)
     [:div
      [:hr]
      [:h3 (i18n/t :debug/title)]
      [:section.download
       [:h3 "@download*"]
       [:pre (with-out-str (pprint @download*))]]
      [:hr]])])

(defn breadcrumb-step-label []
  (cond
    (-> @download* :download-finished)
    (if (:download-cancelled @download*)
      (i18n/t :download/breadcrumb-cancelled)
      (i18n/t :download/breadcrumb-finished))

    (-> @download* :download-started)
    (i18n/t :download/breadcrumb-running)

    (-> @download* :step2-completed)
    (i18n/t :download/breadcrumb-step3)

    (-> @download* :step1-completed)
    (i18n/t :download/breadcrumb-step2)

    :else
    (i18n/t :download/breadcrumb-step1)))

(defn page []
  [:div.page.download
   [breadcrumb/page-breadcrumb
    (i18n/t :download/title)
    (breadcrumb-step-label)]
   (cond
     (-> @download* :download-finished) [download/downloaded-component]
     (-> @download* :download-started) [download/downloading-component]
     (-> @download* :step2-completed) [step3/component]
     (-> @download* :step1-completed) [step2/component]
     :else [step1/component])
   [debug-component]])
