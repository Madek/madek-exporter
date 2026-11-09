(ns madek.app.front.state.electron-main-sync
  (:require
    [madek.app.front.bridge :as bridge]
    [timothypratley.patchin :as patchin]
    [clojure.walk]
    ))

(defn init [electron-main-db]
  (bridge/on-db-full
    (fn [data]
      (reset! electron-main-db (-> data js->clj clojure.walk/keywordize-keys))))

  (bridge/on-db-patch
    (fn [data]
      (swap! electron-main-db
             (fn [db patch]
               (patchin/patch db patch))
             (-> data js->clj clojure.walk/keywordize-keys)))))
