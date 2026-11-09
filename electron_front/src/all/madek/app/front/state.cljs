(ns madek.app.front.state
  (:require
    [madek.app.front.bridge :as bridge]
    [madek.app.front.state.jvm-sync :as jvm-sync]
    [madek.app.front.state.electron-main-sync :as electron-main-sync]

    [accountant.core :as accountant]
    [cljs-uuid-utils.core :as uuid]
    [cljs.core.async :as async :refer (<! >! put! chan)]
    [cljsjs.moment]
    [reagent.core :as reagent]
    [timothypratley.patchin :as patchin]
    [clojure.walk]
    ))

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

(bridge/on-jvm-sync-init
  (fn [data]
    (let [opts (-> data js->clj clojure.walk/keywordize-keys)]
      (js/console.log "madek:jvm-sync:init"
                      (clj->js (dissoc opts :jvm-password)))
      (jvm-sync/init jvm-main-db client-db opts))))

(bridge/on-navigate
  (fn [path]
    (when path
      (accountant/navigate! path))))
