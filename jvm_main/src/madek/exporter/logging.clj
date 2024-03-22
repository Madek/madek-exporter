(ns madek.exporter.logging
  (:require
   [taoensso.timbre :as timbre :refer [debug info]]
   [taoensso.timbre.tools.logging]))

(def DEFAULT_LOGGING_CONFIG
  {:min-level [[#{;"madek.exporter.server.*"
                  "madek.exporter.export.*"
                  ;"madek.exporter.connection"
                  ;"madek.exporter.export.*"
                  }:debug]
               [#{"madek.exporter.*"} :info]
               [#{"*"} :warn]]
   :log-level nil})

(defn init []
  (info "initializing logging" DEFAULT_LOGGING_CONFIG)
  (timbre/merge-config! DEFAULT_LOGGING_CONFIG)
  (taoensso.timbre.tools.logging/use-timbre)
  (info "initialized logging" (pr-str timbre/*config*)))
