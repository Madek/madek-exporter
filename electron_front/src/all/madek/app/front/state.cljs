(ns madek.app.front.state
  (:require
    [madek.app.front.state.jvm-sync :as jvm-sync]
    [madek.app.front.state.electron-main-sync :as electron-main-sync]

    [accountant.core :as accountant]
    [cljs-uuid-utils.core :as uuid]
    [cljs.core.async :as async :refer (<! >! put! chan)]
    [cljs.nodejs :as nodejs]
    [cljsjs.moment]
    [reagent.core :as reagent]
    [timothypratley.patchin :as patchin]
    [clojure.walk]
    ))

(def Electron (nodejs/require "electron"))

;### dbs ######################################################################

(defonce current-page (reagent/atom nil))

(defonce current-path (reagent/atom "/connection"))

(defonce jvm-main-db (reagent/atom {}))

(defonce electron-main-db (reagent/atom {}))

(defonce client-db
  (reagent/atom
    {:debug false
     :app-logs []
     :client-id (uuid/uuid-string (uuid/make-random-uuid))
     :connection {:form {:url nil
                         :login nil
                         :password nil
                         }}}))

(def MAX-APP-LOGS 300)

(defn add-app-log! [level event data]
  (let [entry {:ts (.toISOString (js/Date.))
               :level level
               :event event
               :data data}]
    (swap! client-db update :app-logs
           (fn [logs]
             (->> (conj (vec (or logs [])) entry)
                  (take-last MAX-APP-LOGS)
                  vec)))))

;### init #####################################################################

(defn init []
  (electron-main-sync/init electron-main-db))

(.on (.-ipcRenderer Electron) "madek:jvm-sync:init"
     (fn [event data]
       (let [opts (-> data js->clj clojure.walk/keywordize-keys)]
         (js/console.log "madek:jvm-sync:init" opts)
         (jvm-sync/init jvm-main-db client-db opts)
         )))

(.on (.-ipcRenderer Electron) "madek:navigate"
     (fn [_ path]
       (when path
         (accountant/navigate! path))))

