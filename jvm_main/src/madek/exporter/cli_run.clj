(ns madek.exporter.cli-run
  (:require
   [clojure.pprint :refer [pprint]]
   [clojure.string]
   [clojure.tools.cli :as cli]
   [environ.core :refer [env]]
   [madek.exporter.connection :refer [connect-to-madek-server]]
   [madek.exporter.export :as export]
   [madek.exporter.state :as state]
   [taoensso.timbre :as timbre :refer [info debug warn error]]))

(def cli-options
  [["-u" "--madek-url  URL" "Madek URL"
    :default (or (env :madek-url) "https://staging.madek.zhdk.ch")]
   ["-d" "--download-dir DIRECTORY" "Download directory"
    :default (clojure.string/join java.io.File/separator
                                  [(System/getProperty "user.home")
                                   "Downloads" "Madek"])]
   [nil "--media-entry-id MEDIA-ENTRY-ID"]
   [nil "--token TOKEN"]
   ["-h" "--help"]])

(defn main-usage [options-summary & more]
  (->> ["Usage: madek-exporter <opts> cli <opts>"
        ""
        options-summary
        ""
        ""
        (when more
          ["-------------------------------------------------------------------"
           (with-out-str (pprint more))
           "-------------------------------------------------------------------"])]
       flatten
       (clojure.string/join \newline)))

(defn run [options]
  (info 'run options)
  (connect-to-madek-server {:body {:password (:token options)
                                   :url (:madek-url options)}})
  (info 'state/db @state/db)
  (when-let [media-entry-id (:media-entry-id options)]
    (export/download-media-entry
     media-entry-id
     ""
     (str (-> @state/db :connection :url) "/api")
     (-> @state/db :connection :http-options))))

(defn main [global-options args]
  (try
    (let [{:keys [options arguments errors summary]}
          (cli/parse-opts args cli-options :in-order true)
          cmd (some-> arguments first keyword)
          pass-on-args (->> (rest arguments) flatten (into []))
          options (into (sorted-map) options)
          print-summary #(println (main-usage summary {:args args :options options}))]
      (info {'args args 'options options 'cmd cmd 'pass-on-args pass-on-args})
      (cond
        (:help options) (print-summary)
        :else (run (merge global-options options))))
    (catch Exception ex
      (error ex))))
