(ns madek.exporter.cli-run
  (:require
   [clojure.pprint :refer [pprint]]
   [clojure.string]
   [clojure.tools.cli :as cli]
   [environ.core :refer [env]]
   [madek.exporter.connection :refer [connect-to-madek-server]]
   [madek.exporter.export :as export]
   [madek.exporter.state :as state]
   [madek.exporter.utils :as utils]
   [taoensso.timbre :as timbre :refer [info error]]))

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

(defn- redacted-options [options]
  (cond-> options
    (:token options) (assoc :token "***redacted***")))

(defn run [options]
  (info 'run (redacted-options options))
  (let [resp (connect-to-madek-server
              {:body {:api-token (:token options)
                      :url (:madek-url options)
                      :sign-in-method :token}})
        status (:status resp)]
    (info 'connect-status status)
    (info 'state/db (utils/client-safe-db @state/db))
    (when-not (<= 200 status 299)
      (throw (ex-info "CLI connect failed"
                      {:status status :body (:body resp)})))
    (when-let [media-entry-id (:media-entry-id options)]
      (let [target-dir (utils/validate-target-directory
                        (or (:download-dir options)
                            (get-in @state/db [:download :target-directory])))
            entry-point (str (-> @state/db :connection :url) "/api")
            http-opts (state/connection-http-options)]
        (export/download-media-entry
         media-entry-id
         target-dir
         false
         nil
         entry-point
         http-opts)))))

(defn main [global-options args]
  (try
    (let [{:keys [options arguments errors summary]}
          (cli/parse-opts args cli-options :in-order true)
          cmd (some-> arguments first keyword)
          pass-on-args (->> (rest arguments) flatten (into []))
          options (into (sorted-map) options)
          print-summary #(println (main-usage summary {:args args :options options}))]
      (info {'args args
             'options (redacted-options options)
             'cmd cmd
             'pass-on-args pass-on-args})
      (cond
        (:help options) (print-summary)
        :else (run (merge global-options options))))
    (catch Exception ex
      (error ex "cli-run failed")
      (throw ex))))
