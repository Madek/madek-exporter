(ns madek.app.server.server
  (:require
    [madek.app.server.web :as web]
    [madek.app.server.utils :as utils :refer [presence]]

    [clojure.data.codec.base64 :as base64]
    [environ.core :refer [env]]
    ;[ring.adapter.jetty :refer [run-jetty]]
    ;[ring.adapter.undertow :refer [run-undertow]]
    [immutant.web :refer [run]]
    [clojure.walk]

    [logbug.catcher :as catcher]
    [logbug.thrown]
    [clojure.tools.logging :as logging]

    [logbug.catcher :as catcher]
    [logbug.thrown]
    [logbug.debug :as debug :refer [ I> ]]
    [logbug.ring :as logbug-ring :refer [wrap-handler-with-logging]]

    )
  (:gen-class))


(def web-server (atom nil))

;;; Basic Auth ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

; Basic Auth ist not yet enabled since it is not yet supported, see TODO
; inthe initialize section below

(def unauthorized-401
  {:status 401
   :headers
   {"WWW-Authenticate"
    (str "Basic realm=\"Madek APP - "
         "Password Required!\"")}})

(defn- decode-base64
  [^String string]
  (apply str (map char (base64/decode (.getBytes string)))))


(defn http-basic-authorize [request handler password]
  (if-let [auth-header (-> request clojure.walk/keywordize-keys
                           :headers :authorization)]
    (let [decoded-val (decode-base64 (last (re-find #"^Basic (.*)$" auth-header)))
          [_ request-password] (clojure.string/split (str decoded-val) #":" 2)]
      (if (= password request-password)
        (handler request)
        unauthorized-401))
    unauthorized-401))

(defn wrap-http-basic-auth [handler password]
  (fn [request]
    (http-basic-authorize request handler password)))


;;; Initialize ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn initialize [server-config app-config]
  (let [app (if-let [password (-> app-config :password presence)]
              (wrap-http-basic-auth #'web/app password)
              #'web/app)]
    ; TODO replace #'web/app with app once the chrome in electron supports
    ; Basic Auth via websockets
    ; * this seems to be implemented in chrome now,
    ;  see https://bugs.chromium.org/p/chromium/issues/detail?id=123862
    ; *  Electron 1.6.8 beta still ships with chrome 56
    (try (reset! web-server (run #'web/app server-config))
         (logging/info "Initialized web server" server-config @web-server)
         ;(utils/os-browse uri)
         (catch java.lang.RuntimeException e
           (if (instance? java.net.BindException (.getCause e))
             (do (logging/warn "The port is already occupied! Opening application and bailing out!")
                 ;(utils/os-browse uri)
                 (utils/exit 0))
             (throw e))))))



;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'ring.middleware.resource)
;(debug/debug-ns *ns*)
