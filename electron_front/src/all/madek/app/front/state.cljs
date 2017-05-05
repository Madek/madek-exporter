(ns madek.app.front.state
  (:require
    [cljs-uuid-utils.core :as uuid]
    [cljs.core.async :as async :refer (<! >! put! chan)]
    [cljs.nodejs :as nodejs]
    [cljsjs.moment]
    [reagent.core :as reagent]
    [taoensso.sente  :as sente :refer (cb-success?)]
    [timothypratley.patchin :as patchin]
    [clojure.walk]
    ))

(def Electron (nodejs/require "electron"))

;### dbs ######################################################################

(defonce current-page (reagent/atom nil))

(defonce jvm-main-db (reagent/atom {}))

(defonce electron-main-db (reagent/atom {}))

(defonce client-db (reagent/atom {:debug false
                                  :client-id (uuid/uuid-string (uuid/make-random-uuid))}))


;### electron main db #########################################################

(.on (.-ipcRenderer Electron) "db-full"
     (fn [event data]
       (reset! electron-main-db (-> data js->clj clojure.walk/keywordize-keys))
       (js/console.log "db-full" (-> data clj->js clojure.walk/keywordize-keys))))

(.on (.-ipcRenderer Electron) "db-patch"
     (fn [event data]
       (js/console.log "db-patch"  (clj->js (type data)))
       (swap! electron-main-db
              (fn [db patch]
                (js/console.log {:db db :patch patch})
                (patchin/patch db patch))
              (-> data js->clj clojure.walk/keywordize-keys))))


;### jvm db ###################################################################


(when false

  (let [{:keys [chsk ch-recv send-fn state]}
        (sente/make-channel-socket! "localhost:8383/chsk" ; Note the same path as before
                                    {:type :auto ; e/o #{:auto :ajax :ws}
                                     :client-id (:client-id @client-db)
                                     })]
    (def chsk       chsk)
    (def ch-chsk    ch-recv) ; ChannelSocket's receive channel
    (def chsk-send! send-fn) ; ChannelSocket's send API fn
    (def chsk-state state)   ; Watchable, read-only atom)
    )

  (defn patch-db [patch-data]
    (swap! jvm-main-db
           (fn [db diff]
             (patchin/patch db diff))
           (:diff patch-data)))

  (defn event-msg-handler [{:as message :keys [id ?data event]}]
    ;(js/console.log (clj->js {:message message}))
    (when (= id :chsk/recv)
      (let [[event-id data] ?data]
        (when (and event-id data)
          (js/console.log (clj->js {:event-id event-id :data data}))
          (case event-id
            :madek/db (reset! jvm-main-db data)
            :madek/patch (patch-db data)
            )))))

  (def message-router (atom nil))
  (defn stop-message-router [] (when-let [stop-f @message-router] (stop-f)))
  (defn start-message-router []
    (stop-message-router)
    (reset! message-router (sente/start-chsk-router! ch-chsk event-msg-handler)))

  (start-message-router)

  )


