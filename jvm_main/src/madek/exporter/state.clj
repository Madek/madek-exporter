(ns madek.exporter.state
  (:require
   [clojure.java.io :as io]
   [clojure.set :refer [difference]]
   [compojure.core :as cpj]
   [logbug.catcher :as catcher]
   [logbug.debug :as debug :refer [I>]]
   [logbug.ring :as logbug-ring :refer [wrap-handler-with-logging]]
   [logbug.thrown]
   [madek.exporter.utils :as utils :refer [exit presence deep-merge]]
   [taoensso.sente :as sente]
   [taoensso.sente.server-adapters.immutant :refer [sente-web-server-adapter]]
   [taoensso.timbre :refer [info debug warn]]
   [timothypratley.patchin :as patchin]))

;### THE DATABASE #############################################################

(def default-download-directory
  (clojure.string/join java.io.File/separator
                       [(System/getProperty "user.home")
                        "Downloads" "Madek-Export"]))

(defonce db (atom {:download {:target-directory default-download-directory}}))
;(reset! db {:download {:target-directory default-download-directory}})

(swap! db assoc-in [:download :state] :step1)

(comment
  (:connection @db))

(defonce clients (atom {}))

;### connection params ########################################################

(defn connection-entry-point []
  (let [url (or (-> @db :connection :url)
                (throw (ex-info "The connection entry-point is not available at this time." {})))]
    (str url "/api")))

(defn connection-http-options []
  (or (-> @db :connection :http-options)
      (throw (ex-info "The connection options are not available at this time." {}))))

;### sente setup ##############################################################

(declare chsk-send! connected-uids)

(defn initialize-sente []
  (let [{:keys [ch-recv send-fn ajax-post-fn
                ajax-get-or-ws-handshake-fn connected-uids]}
        (sente/make-channel-socket!
         sente-web-server-adapter
         {:user-id-fn (fn [req] (:client-id req))})]
    (def ring-ajax-post ajax-post-fn)
    (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
    (def ch-chsk ch-recv) ; ChannelSocket's receive channel
    (def chsk-send! send-fn) ; ChannelSocket's send API fn
    (def connected-uids connected-uids) ; Watchable, read-only atom)
    ))
(defn ^:private routes [default-handler]
  (cpj/routes
   (cpj/GET "/chsk" req (ring-ajax-get-or-ws-handshake req))
   (cpj/POST "/chsk" req (ring-ajax-post req))
   (cpj/ANY "*" _ default-handler)))

(defn wrap [handler]
  (I> (wrap-handler-with-logging :trace)
      handler
      routes))

;### publish database #########################################################

(defonce last-client-pushes (atom {}))

(defn swap-in-last-db [client-id data]
  (swap! last-client-pushes
         (fn [last-client-pushes client-id data]
           (assoc last-client-pushes client-id data))
         client-id data))

(defn push-full [client-id data]
  (chsk-send! client-id [(keyword "madek" "db") data])
  (swap-in-last-db client-id data))

(defn diff [a b]
  (patchin/diff a b))

(defn push-diff [client-id lastly-pushed-data data]
  (chsk-send! client-id [(keyword "madek" "patch")
                         {:diff (diff lastly-pushed-data data)
                          :checksum nil}])
  (swap-in-last-db client-id data))

(defn publish [client-id event-id data]
  (if-let [lastly-pushed-data (get @last-client-pushes client-id)]
    (push-diff client-id lastly-pushed-data data)
    (push-full client-id data)))

(defn watch-db [_ _ _ new-state]
  (doseq [client (keys @clients)]
    (publish client "db" new-state)))

;### manage clients ###########################################################

(defn watch-connected-uids [_ _ old-state new-state]
  (let [current-clients (-> new-state :any)
        removed-clients (difference (-> @clients keys set) current-clients)
        added-clients (difference current-clients (-> @clients keys set))]
    (debug {:current-clients current-clients
            :removed-clients removed-clients
            :added-clients added-clients})
    (doseq [removed-client removed-clients]
      (swap! clients (fn [cls cid] (dissoc cls cid)) removed-client))
    (doseq [added-client added-clients]
      (swap! clients (fn [cls cid] (assoc cls cid {})) added-client)
      (publish added-client "db" @db))))

;### initialize ###############################################################

(defonce initialized* (atom false))

(defn initialize [initial-db]
  (if @initialized*
    (warn "skipping db reinitialization")
    (do (swap! db deep-merge initial-db)
        (initialize-sente)
        (add-watch connected-uids :connected-uids #'watch-connected-uids)
        (add-watch db :db #'watch-db)
        (info "initialized state" {:db @db})
        (reset! initialized* true))))

;### Debug ####################################################################
;(debug/debug-ns 'ring.middleware.resource)
;(debug/debug-ns *ns*)

