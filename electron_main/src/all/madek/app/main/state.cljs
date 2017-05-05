(ns madek.app.main.state
  (:require
    [cljs.nodejs :as nodejs]
    [madek.app.main.path-resolver]
    [timothypratley.patchin :as patchin]))

(def fs (nodejs/require "fs"))

(def package-json
  (if-let [package-file (madek.app.main.path-resolver/resolve-path
                          "../package.json")]
    (.parse js/JSON (.readFileSync fs package-file))
    nil))

(def db (atom {:timestamp (.now js/Date)
               :environment
               {:nodejs-version (.-version nodejs/process)
                }}))

(js/setInterval #(swap! db assoc :timestamp (.now js/Date)) 1000)

(add-watch
  db :db-change-logger
  (fn [_ _ old-state new-state]
    (.log js/console "db changed"
          (-> (patchin/diff old-state new-state) clj->js))))


