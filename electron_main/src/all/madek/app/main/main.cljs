(ns madek.app.main.main
  (:require
    [cljs.nodejs :as nodejs]

    [madek.app.main.jvm-main-process]
    [madek.app.main.file-logger :as file-log]
    [madek.app.main.menu]
    [madek.app.main.windows]
    ))

(def Electron (nodejs/require "electron"))

; this will enable the "inspect" context menu; only in dev mode
(when (= madek.app.main.env/env :dev)
  (let [context-menu-module (nodejs/require "electron-context-menu")
        context-menu-fn (or (.-default context-menu-module)
                            context-menu-module)]
    (when context-menu-fn
      (context-menu-fn
        (clj->js
          {:prepend
           (fn [_params _win]
             (clj->js [{:label "Rainbow"
                        :visible (fn [params] (= (.-mediaType params) "image"))}]))})))))

(def crash-reporter (.-crashReporter Electron))

(def Os (nodejs/require "os"))

(def app (.-app Electron))

;; Citrix/VDI and other environments often fail Chromium's GPU process
;; (error_code=18 / "GPU process isn't usable"). Disable before app ready.
(.disableHardwareAcceleration app)
(.appendSwitch (.-commandLine app) "disable-gpu")
(.appendSwitch (.-commandLine app) "in-process-gpu")

;; Zip-distributed Linux builds cannot ship a working setuid chrome-sandbox.
(when (= (.-platform nodejs/process) "linux")
  (.appendSwitch (.-commandLine app) "no-sandbox")
  (.appendSwitch (.-commandLine app) "disable-setuid-sandbox"))

(.log js/console "__dirname" (js* "__dirname"))

(defn -main []
  (file-log/init!)
  (if-not (.requestSingleInstanceLock app)
    (do
      (file-log/log! "WARN" "Another instance already running; quitting")
      (.quit app))
    (do
      (file-log/log! "INFO" (str "Start Madek application on " (.type Os) "."))
      (file-log/log! "INFO" "GPU disabled (disableHardwareAcceleration, disable-gpu, in-process-gpu)")
      (when (= (.-platform nodejs/process) "linux")
        (file-log/log! "INFO" "Linux sandbox disabled (no-sandbox, disable-setuid-sandbox)"))
      (.on app "second-instance"
           (fn [_args _working-directory]
             (file-log/log! "INFO" "second-instance; focusing existing window")
             (madek.app.main.windows/focus-any)))
      (.start crash-reporter
              (clj->js
                {:productName "Madek"
                 :submitURL   "https://wiki.zhdk.ch/madek-hilfe/doku.php"
                 :uploadToServer false }))
      (madek.app.main.jvm-main-process/init app)
      (.on nodejs/process "error"
           (fn [err]
             (file-log/log! "ERROR" "process:error" {:error (str err)})
             (.log js/console err)))
      (.on nodejs/process "uncaughtException"
           (fn [err]
             (file-log/log! "ERROR" "process:uncaughtException" {:error (str err)})
             (.error js/console err)))
      (.on nodejs/process "unhandledRejection"
           (fn [reason _promise]
             (file-log/log! "ERROR" "process:unhandledRejection" {:reason (str reason)})))
      (.on app "window-all-closed"
           (fn []
             (.quit app)))
      (.on app "ready" (fn []
                          (madek.app.main.windows/init-ipc)
                          (madek.app.main.menu/initialize)
                          (madek.app.main.windows/open-new))))))

(nodejs/enable-util-print!)

(.log js/console (str "Start Madek application on " (.type Os) "."))

(set! *main-cli-fn* -main)
