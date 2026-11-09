(ns madek.exporter.server.http-server
  (:require
   [clojure.data.codec.base64 :as base64]
   [clojure.tools.logging :as logging]
   [clojure.walk]
   [environ.core :refer [env]]
   [logbug.catcher :as catcher]
   [logbug.debug :as debug :refer [I>]]
   [logbug.ring :as logbug-ring :refer [wrap-handler-with-logging]]
   [logbug.thrown]
   [madek.exporter.server.web :as web]
   [madek.exporter.utils :as utils :refer [presence]]
   [org.httpkit.server :as http-kit]))

(def web-server (atom nil))

;;; Basic Auth ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def unauthorized-401
  {:status 401
   :headers
   {"WWW-Authenticate"
    (str "Basic realm=\"Madek-Exporter - "
         "Password Required!\"")}})

(defn- decode-base64
  [^String string]
  (try
    (String. ^bytes (base64/decode (.getBytes string "UTF-8")) "UTF-8")
    (catch Exception _
      nil)))

(defn- constant-time-eq?
  "Compare secrets without short-circuiting on the first differing byte."
  [^String a ^String b]
  (let [aa (or a "")
        bb (or b "")
        ba (.getBytes aa "UTF-8")
        bb-bytes (.getBytes bb "UTF-8")
        len (max (alength ba) (alength bb-bytes))]
    (loop [i 0 diff (bit-xor (alength ba) (alength bb-bytes))]
      (if (< i len)
        (let [x (if (< i (alength ba)) (aget ba i) 0)
              y (if (< i (alength bb-bytes)) (aget bb-bytes i) 0)]
          (recur (inc i) (bit-or diff (bit-xor x y))))
        (zero? diff)))))

(defn http-basic-authorize [request handler password]
  (if-let [auth-header (or (get-in request [:headers "authorization"])
                           (get-in request [:headers :authorization]))]
    (if-let [[_ b64] (re-find #"(?i)^Basic\s+(.+)$" (str auth-header))]
      (if-let [decoded-val (decode-base64 b64)]
        (let [[_ request-password] (clojure.string/split decoded-val #":" 2)]
          (if (constant-time-eq? password request-password)
            (handler request)
            unauthorized-401))
        unauthorized-401)
      unauthorized-401)
    unauthorized-401))

(defn wrap-http-basic-auth [handler password]
  (fn [request]
    (http-basic-authorize request handler password)))

;;; Initialize ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- http-kit-options
  "Map Immutant-style {:port :host} to http-kit {:port :ip}."
  [{:keys [port host]}]
  (cond-> {:port port}
    host (assoc :ip host)))

(defn- loopback-host?
  [host]
  (contains? #{"localhost" "127.0.0.1" "::1" "0:0:0:0:0:0:0:1"}
             (some-> host str clojure.string/lower-case)))

(defn initialize [server-config app-config]
  (let [password (-> app-config :password presence)
        host (:host server-config)
        _ (when (and host (not (loopback-host? host)) (not password))
            (throw (ex-info
                    "Refusing to bind off-loopback without a Basic Auth password"
                    {:host host})))
        app (if password
              (wrap-http-basic-auth #'web/app password)
              #'web/app)
        opts (http-kit-options server-config)]
    (try (reset! web-server (http-kit/run-server app opts))
         (logging/info "Initialized web server"
                       (cond-> opts
                         password (assoc :basic-auth? true)))
         (catch java.net.BindException e
           (logging/warn "The port is already occupied! Opening application and bailing out!")
           (utils/exit 0))
         (catch java.lang.RuntimeException e
           (if (instance? java.net.BindException (.getCause e))
             (do (logging/warn "The port is already occupied! Opening application and bailing out!")
                 (utils/exit 0))
             (throw e))))))

;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'ring.middleware.resource)
;(debug/debug-ns *ns*)
