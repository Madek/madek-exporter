(ns madek.app.front.about
  (:require
    [fipp.edn :refer [pprint]]
    [madek.app.front.state :as state]
    [madek.app.front.env]
    [madek.app.front.release :as release]
    [cljs.nodejs :as nodejs]
    ))

(defn version-component []
  [:div.version
   [:h2 "Version and Release"]
   [:p "Version " [:code @release/version*]]
   [release/release-info-component]])

(defn electron-component []
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
      (doall
        (for [[k v] (js->clj (.-versions nodejs/process))]
          [:li {:key (str k)} (str k ": " v)]))]]
    [:li
     [:span "Chrome version: "]
     [:span.code (.-appVersion js/navigator)]]]])

(defn page []
  [:div.about
   [:h1 "About the Madek-Exporter"]
   [version-component]
   [electron-component]
   ])

