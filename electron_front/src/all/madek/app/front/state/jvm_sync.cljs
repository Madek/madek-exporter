(ns madek.app.front.state.jvm-sync
  (:require
    [taoensso.sente  :as sente :refer (cb-success?)]
    [timothypratley.patchin :as patchin]
    ))


(defn init [jvm-main-db client-db opts]

  (let [{port :jvm-port password :jvm-password} opts
        {:keys [chsk ch-recv send-fn state jvm-port]}
        (sente/make-channel-socket! "/chsk"
          {:type :auto ; e/o #{:auto :ajax :ws}
           ; TODO we add parameters for basic auth here; it is not supported by chome yet
           ; and hence disabled on the server side;
           ; I am not sure if this is the right place here; but is seems as it doesn't
           ; mess up the host; chrome seems just to remove it
           :host (str (:client-id @client-db) ":" password "@localhost:" port)
           :protocol :http
           :client-id (:client-id @client-db)})]
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
          ;(js/console.log (clj->js {:event-id event-id :data data}))
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



