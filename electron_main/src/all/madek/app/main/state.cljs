(ns madek.app.main.state
  (:require
    [madek.app.main.env :as env]

    [cljs.nodejs :as nodejs]
    [timothypratley.patchin :as patchin]
    ))

(def fs (nodejs/require "fs"))

(def package-json
  (.parse js/JSON (.readFileSync fs (str env/app-dir "/" "package.json"))))

(def db (atom {:timestamp (.now js/Date)
               :environment
               {:nodejs-version (.-version nodejs/process)
                :package-json package-json}
               :connection {:status nil}}))

(js/setInterval #(swap! db assoc :timestamp (.now js/Date)) 1000)

(add-watch
  db :db-change-logger
  (fn [_ _ old-state new-state]
    ;(.log js/console "db changed" (-> (patchin/diff old-state new-state) clj->js))
    ))


