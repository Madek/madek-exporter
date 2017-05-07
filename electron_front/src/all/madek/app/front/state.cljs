(ns madek.app.front.state
  (:require
    [madek.app.front.state.jvm-sync :as jvm-sync]
    [madek.app.front.state.electron-main-sync :as electron-main-sync]

    [cljs-uuid-utils.core :as uuid]
    [cljs.core.async :as async :refer (<! >! put! chan)]
    [cljs.nodejs :as nodejs]
    [cljsjs.moment]
    [reagent.core :as reagent]
    [timothypratley.patchin :as patchin]
    [clojure.walk]
    ))


;### dbs ######################################################################

(defonce current-page (reagent/atom nil))

(defonce jvm-main-db (reagent/atom {}))

(defonce electron-main-db (reagent/atom {}))

(defonce client-db
  (reagent/atom
    {:debug false
     :client-id (uuid/uuid-string (uuid/make-random-uuid))
     :connection {:form {:url "https://test.madek.zhdk.ch"
                         :login nil
                         :password nil
                         }}}))

;### init #####################################################################

(defn init []
  (electron-main-sync/init electron-main-db)
  (jvm-sync/init jvm-main-db client-db))
