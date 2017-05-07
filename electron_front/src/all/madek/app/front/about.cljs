(ns madek.app.front.about
  (:require
    [fipp.edn :refer [pprint]]
    [madek.app.front.state :as state]
    [madek.app.front.env]
    [cljs.nodejs :as nodejs]
    ))

(defn content []
  [:div.content
   [:div.build
    [:h2 "Built"]
    [:p "Version: "
     [:span.env (-> @state/electron-main-db
                    :environment :package-json :version)]]
    [:p "Environment: "
     [:span.env madek.app.front.env/env]]]
   [:div.electron
    [:h2 "Electron Components"]
    [:ul
     [:li
      [:span "node.js version: "]
      [:span.code (-> @state/electron-main-db
                      :environment :nodejs-version)]]
     [:li
      [:span "Electron version: "]
      [:span.code (-> nodejs/process .-versions .-electron)]]
     [:li
      [:span "Electron modules"]
      [:ul
       (for [[k v] (-> nodejs/process .-versions js->clj)]
         [:li {:key k}(str k ": " v)])]
      [:span.code (-> nodejs/process .-versions js->clj)]]
     [:li
      [:span "Chrome version: "]
      [:span.code (.-appVersion js/navigator)]]]]])

(defn page []
  [:div.about
   [:h1 "About the Madek App"]
   [content]
   ])

