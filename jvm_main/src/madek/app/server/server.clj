(ns madek.app.server.server
  (:require
    [madek.app.server.web :as web]
    [madek.app.server.utils :as utils]

    [environ.core :refer [env]]
    ;[ring.adapter.jetty :refer [run-jetty]]
    ;[ring.adapter.undertow :refer [run-undertow]]
    [immutant.web :refer [run]]

    [logbug.catcher :as catcher]
    [logbug.thrown]
    [clojure.tools.logging :as logging]

    )
  (:gen-class))


(def web-server (atom nil))

(defn initialize [config]
  (let [uri (str "http://" (:host config) ":" (:port config)"/")]
    (try
      (reset! web-server  (run #'web/app config))
      (logging/info "Initialized web server" config @web-server)
      ;(utils/os-browse uri)
      (catch java.lang.RuntimeException e
        (if (instance? java.net.BindException (.getCause e))
          (do (logging/warn "The port is already occupied! Opening application and bailing out!")
              ;(utils/os-browse uri)
              (utils/exit 0))
          (throw e))))))
