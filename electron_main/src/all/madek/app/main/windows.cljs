(ns madek.app.main.windows
  (:require
    [madek.app.main.state]
    [madek.app.main.env :as env]

    [cljs-uuid-utils.core :as uuid]
    [cljs.nodejs :as nodejs]
    [clojure.set :refer [difference]]


    [timothypratley.patchin :as patchin]
    ))

(declare send-db-full send-db)

(def Electron (nodejs/require "electron"))

(def BrowserWindow (.-BrowserWindow Electron))

(def windows (atom {}))

(add-watch
  windows :windows-change-logger
  (fn [_ _ _ new-state]
    (.log js/console "windows changed" (-> new-state keys clj->js))))

(defn- show-and-focus [window]
  (when window
    (when (.isMinimized window)
      (.restore window))
    (.show window)
    (.focus window)))

(defn focus-any []
  (when-let [win-a (->> @windows vals first)]
    (show-and-focus (:window @win-a))))

(defn open-new []
  (let [id (-> (uuid/make-random-uuid) uuid/uuid-string)
        window (BrowserWindow. (clj->js {:width 800 :height 600
                                         :show false
                                         :webPreferences {:nodeIntegration true
                                                          :contextIsolation false}}))
        index-html-path (str env/app-dir "/index.html")
        shown? (atom false)
        ensure-shown (fn []
                       (when (compare-and-set! shown? false true)
                         (show-and-focus window)))]
    (.log js/console "index-html-path" index-html-path)
    (.loadURL window (str "file://" index-html-path))
    (swap! windows assoc id (atom {:window window}))
    ;; ready-to-show can fail to fire on some Linux/Wayland file-manager launches;
    ;; also show on did-finish-load and a short timeout so the first click works.
    (.once window "ready-to-show" ensure-shown)
    (.once (.-webContents window) "did-finish-load" ensure-shown)
    (js/setTimeout ensure-shown 2000)
    (.on (.-webContents window) "dom-ready"
         (fn []
           (.send (.-webContents window) "madek:jvm-sync:init"
                  (clj->js {:jvm-port env/jvm-port
                            :jvm-password env/jvm-password}))
           (send-db-full id)))
    (.on window "closed" (fn [] (swap! windows dissoc id)))))

(defn send-db-full
  ([client-id]
   (send-db-full client-id @madek.app.main.state/db))
  ([client-id db]
   (when-let [win-a (get @windows client-id)]
     (send-db-full client-id db win-a)))
  ([client-id db win-a]
   ;(.log js/console "send-db-full" (clj->js [client-id db]))
   (let [wc (-> @win-a :window .-webContents)]
     (.send wc "madek:db:full" (clj->js db))
     (swap! win-a assoc :db db))))


(defn send-db-patch [client-id new-client-db win-a]
  (let [current-client-db (:db @win-a)
        db-patch (patchin/diff current-client-db new-client-db)
        wc (-> @win-a :window .-webContents)]
    ;(.log js/console "send-db-patch" (clj->js {:current-client-db current-client-db
    ;                                           :new-client-db new-client-db
    ;                                           :db-patch db-patch}))
    (.send wc "madek:db:patch" (clj->js db-patch))
    (swap! win-a assoc :db new-client-db)))

(defn send-db
  ([client-id]
   (send-db client-id @madek.app.main.state/db))
  ([client-id db]
   (when-let [win-a (get @windows client-id)]
     (if (:db @win-a)
       (send-db-patch client-id db win-a)
       (send-db-full client-id db win-a)))))


;
;(add-watch windows :initial-state-push (fn [_ _ new-state old-state] (->> (difference (keys new-state) (keys old-state)) (map send-db-full))))

(add-watch
  madek.app.main.state/db :push-to-client-listenter
  (fn [_ _ _ new-state]
    (doseq [client-id (keys @windows)]
      (send-db client-id new-state))))
