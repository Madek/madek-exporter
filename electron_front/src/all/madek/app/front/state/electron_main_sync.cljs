(ns madek.app.front.state.electron-main-sync
  (:require
    [cljs.nodejs :as nodejs]
    [timothypratley.patchin :as patchin]
    ))

(def Electron (nodejs/require "electron"))

(defn init [electron-main-db]

  (.on (.-ipcRenderer Electron) "madek:db:full"
       (fn [event data]
         ;(js/console.log "db-full" (-> data clj->js clojure.walk/keywordize-keys))
         (reset! electron-main-db (-> data js->clj clojure.walk/keywordize-keys))
         ))

  (.on (.-ipcRenderer Electron) "madek:db:patch"
       (fn [event data]
         ;(js/console.log "db-patch"  (clj->js (type data)))
         (swap! electron-main-db
                (fn [db patch]
                  ;(js/console.log {:db db :patch patch})
                  (patchin/patch db patch))
                (-> data js->clj clojure.walk/keywordize-keys))))
  )
