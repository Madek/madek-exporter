(ns madek.exporter.main
  (:gen-class)
  (:require
   [clojure.pprint :refer [pprint]]
   [clojure.string]
   [clojure.tools.cli :as cli]
   [madek.exporter.cli-run :as cli-run]
   [madek.exporter.logging :as logging]
   [madek.exporter.repl :as repl]
   [madek.exporter.server.main :as server]
   [taoensso.timbre :as timbre :refer [info debug warn error]]))

(def cli-options
  (concat
   [["-h" "--help"]]
   repl/cli-options))

(defn main-usage [options-summary & more]
  (->> ["Usage: madek-exporter <opts> SCORE <opts>"
        "scopes: server, cli"
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

(defn main [args]
  (try
    (logging/init)
    (let [{:keys [options arguments errors summary]}
          (cli/parse-opts args cli-options :in-order true)
          cmd (some-> arguments first keyword)
          pass-on-args (->> (rest arguments) flatten (into []))
          options (into (sorted-map) options)
          print-summary #(println (main-usage summary {:args args :options options}))]
      (info {'args args 'options options 'cmd cmd 'pass-on-args pass-on-args})
      (repl/init options)
      (cond
        (:help options) (print-summary)
        :else (case cmd
                :server (server/main options pass-on-args)
                :cli (cli-run/main options pass-on-args)
                (print-summary))))
    (catch Exception ex
      (error ex))))

(defonce args* (atom nil))
(when @args* (main @args*))

(defn -main [& args]
  (reset! args* args)
  (main args))

