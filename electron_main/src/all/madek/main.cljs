(ns madek.main
  (:require
    [cljs.nodejs :as nodejs]

    [madek.jvm-main-process]
    [madek.menu]
    [madek.windows]
    ))

(def Electron (nodejs/require "electron"))

(def crash-reporter (.-crashReporter Electron))

(def Os (nodejs/require "os"))

(def app (.-app Electron))


(defn -main []
  (.start crash-reporter
          (clj->js
            {:productName "Madek"
             :companyName "ZHdK"
             :submitURL   "https://wiki.zhdk.ch/madek-hilfe/doku.php"
             :uploadToServer false }))
  (madek.jvm-main-process/init app)
  (.on nodejs/process "error"
       (fn [err] (.log js/console err)))
  (.on app "window-all-closed"
       (fn [] (if (not= (.-platform nodejs/process) "darwin")
                (.quit app))))
  (.on app "ready" (fn []
                     (madek.menu/initialize)
                     (madek.windows/open-new)
                     )))

(nodejs/enable-util-print!)

(.log js/console (str "Start Madek application on " (.type Os) "."))

(set! *main-cli-fn* -main)
