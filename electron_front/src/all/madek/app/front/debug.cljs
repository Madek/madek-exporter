(ns madek.app.front.debug
  (:require
    [fipp.edn :refer [pprint]]
    [madek.app.front.state :as state]
    [clojure.walk]
    ))

(defn content []
  [:div.content
   [:p "Electron main "
    (if (empty? @state/electron-main-db)
      "waiting ..."
      "connected! ")]
   [:p "JVM main "
    (if (:jvm-main-options  @state/jvm-main-db)
      "connected! "
      "waiting ...")]
   [:div.jvm-main-db
    [:h3 "Electron-main DB"]
    [:pre
     (with-out-str (pprint @state/electron-main-db))]]
   [:div.jvm-main-db
    [:h3 "JVM-main DB"]
    [:pre
     (with-out-str (pprint @state/jvm-main-db))]]
   [:div.current-page
    [:h3 "Current Page"]
    [:pre
     (with-out-str (pprint @state/current-page))]]
   [:div.app-db
    [:h3 "Client DB"]
    [:pre
     (with-out-str (pprint @state/client-db))]]
   ])

(defn page []
  [:div.debug
   [:h1 "Debug"]
   [content]
   ])
