(ns madek.app.main.file-logger
  (:require
    [cljs.nodejs :as nodejs]))

;; Simple file logger for packaged Electron apps.
;; - Writes to: <Electron userData>/logs/app.log
;; - Rotation: when size would exceed ~2MB:
;;   app.log -> app.log.1
;;   new app.log starts empty

(def Electron (nodejs/require "electron"))
(def fs (nodejs/require "fs"))
(def path (nodejs/require "path"))
(def app (.-app Electron))

(def max-bytes (* 2 1024 1024))

(defonce log-dir* (atom nil))
(defonce log-file* (atom nil))
(defonce backup-log-file* (atom nil))

(defn- now-iso []
  (.toISOString (js/Date.)))

(defn- report-error! [operation err]
  (.error js/console (str "File logger failed to " operation ": " err)))

(defn- ensure-init! []
  (when (nil? @log-file*)
    (let [user-data-dir (.getPath app "userData")
          logs-dir (.resolve path user-data-dir "logs")
          log-file (.resolve path logs-dir "app.log")
          backup-log-file (.resolve path logs-dir "app.log.1")]
      (try
        (when-not (.existsSync fs logs-dir)
          (.mkdirSync fs logs-dir (clj->js {:recursive true})))
        (catch :default err
          (report-error! (str "create log directory " logs-dir) err)))
      (reset! log-dir* logs-dir)
      (reset! log-file* log-file)
      (reset! backup-log-file* backup-log-file))))

(defn- file-size [p]
  (try
    (when (.existsSync fs p)
      (.-size (.statSync fs p)))
    (catch :default err
      (report-error! (str "read log size for " p) err)
      0)))

(defn- rotate-if-needed! [incoming-bytes]
  (ensure-init!)
  (let [log-file @log-file*
        backup-file @backup-log-file*
        current-size (file-size log-file)
        new-size (+ current-size incoming-bytes)]
    (when (> new-size max-bytes)
      (try
        (when (.existsSync fs backup-file)
          (.unlinkSync fs backup-file))
        (catch :default err
          (report-error! (str "remove backup log " backup-file) err)))
      (try
        (when (.existsSync fs log-file)
          (.renameSync fs log-file backup-file))
        (catch :default err
          (report-error! (str "rotate log to " backup-file) err))))))

(defn log!
  "Writes a single line to app.log and performs 2MB + 1-backup rotation."
  ([level message]
   (log! level message nil))
  ([level message meta]
   (ensure-init!)
   (let [meta-json (when meta
                     (try
                       (js/JSON.stringify (clj->js meta))
                       (catch :default _ nil)))
         suffix (if meta-json (str " " meta-json) "")
         line (str (now-iso) " [" (str level) "] " (str message) suffix "\n")
         incoming-bytes (js/Buffer.byteLength line "utf8")]
     (try
       (rotate-if-needed! incoming-bytes)
       (.appendFileSync fs @log-file* line "utf8")
       (catch :default err
         (report-error! (str "append to " @log-file*) err))))))

(defn init!
  "Ensures the log directory is created. Safe to call multiple times."
  []
  (ensure-init!)
  @log-dir*)

