(ns madek.app.front.state.jvm-sync
  (:require
    [taoensso.sente  :as sente :refer (cb-success?)]
    [timothypratley.patchin :as patchin]
    ))

(defonce jvm-password* (atom nil))

(defn jvm-password []
  @jvm-password*)

(defn init [jvm-main-db client-db opts]

  (let [{port :jvm-port password :jvm-password} opts
        _ (reset! jvm-password* (str password))
        {:keys [chsk ch-recv send-fn state jvm-port]}
        (sente/make-channel-socket! "/chsk"
          {:type :auto ; e/o #{:auto :ajax :ws}
           ;; URL userinfo supplies Basic Auth for /chsk (WS + AJAX).
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
    (when (= id :chsk/recv)
      (let [[event-id data] ?data]
        (when event-id
          (case event-id
            :madek/download-logs
            (swap! jvm-main-db assoc-in [:download :logs] (vec (or data [])))
            :madek/db
            (when data (reset! jvm-main-db data))
            :madek/patch
            (when data (patch-db data))
            nil)))))

  (def message-router (atom nil))
  (defn stop-message-router [] (when-let [stop-f @message-router] (stop-f)))
  (defn start-message-router []
    (stop-message-router)
    (reset! message-router (sente/start-chsk-router! ch-chsk event-msg-handler)))

  (start-message-router)

  )
