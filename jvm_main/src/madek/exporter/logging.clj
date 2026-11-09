(ns madek.exporter.logging
  (:require
   [taoensso.timbre :as timbre :refer [info]]
   [taoensso.timbre.tools.logging]))

(def DEFAULT_LOGGING_CONFIG
  ;; Prod-friendly defaults: keep exporter breadcrumbs at :info, hush libraries.
  {:min-level [[#{"madek.exporter.*"} :info]
               [#{"*"} :warn]]
   :log-level nil})

(defn init []
  (info "initializing logging" DEFAULT_LOGGING_CONFIG)
  (timbre/merge-config! DEFAULT_LOGGING_CONFIG)
  (taoensso.timbre.tools.logging/use-timbre)
  (info "initialized logging"))
