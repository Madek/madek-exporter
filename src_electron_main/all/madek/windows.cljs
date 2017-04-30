(ns madek.windows
  (:require
    [cljs.nodejs :as nodejs]
    [cljs-uuid-utils.core :as uuid]))

(def path (nodejs/require "path"))

(def fs (nodejs/require "fs"))

(def Electron (nodejs/require "electron"))

(def BrowserWindow (.-BrowserWindow Electron))

(def windows (atom {}))

(add-watch windows :windows-change-logger
           (fn [_ _ _ new-state]
             (.log js/console "windows changed" (-> new-state keys clj->js))))


(defn index-html-path []
  ; path to index.html depends on the cljsbuild optimization setting
  (->> ["../index.html" "../../../index.html"]
       (map #(.resolve path (js* "__dirname") %))
       (filter #(.existsSync fs %))
       first))

(defn open-new-window []
  (let [id (-> (uuid/make-random-uuid) uuid/uuid-string)
        window (BrowserWindow. (clj->js {:width 800 :height 600}))]
    (.loadURL window (str "file://" (index-html-path)))
    (swap! windows assoc id window)
    (.on window "closed" (fn [] (swap! windows dissoc id)))))

