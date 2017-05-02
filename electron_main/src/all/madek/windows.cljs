(ns madek.windows
  (:require
    [cljs.nodejs :as nodejs]
    [cljs-uuid-utils.core :as uuid]
    [madek.path-resolver]
    ))

(def Electron (nodejs/require "electron"))

(def BrowserWindow (.-BrowserWindow Electron))

(def windows (atom {}))

(add-watch windows :windows-change-logger
           (fn [_ _ _ new-state]
             (.log js/console "windows changed" (-> new-state keys clj->js))))

(defn open-new []
  (let [id (-> (uuid/make-random-uuid) uuid/uuid-string)
        window (BrowserWindow. (clj->js {:width 800 :height 600}))]
    (.loadURL window (str "file://" (madek.path-resolver/resolve-path "index.html")))
    (swap! windows assoc id window)
    (.on window "closed" (fn [] (swap! windows dissoc id)))))

