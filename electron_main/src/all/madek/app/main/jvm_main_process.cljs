(ns madek.app.main.jvm-main-process
  (:require
    [madek.app.main.env]
    [madek.app.main.path-resolver]

    [cljs.nodejs :as nodejs]
    ))

(def child-process (nodejs/require "child_process"))

(def jvm-main-process-child (atom nil))

(defn stop []
  (when @jvm-main-process-child
    (.log js/console "stopping jvm-main-process")
    (.kill @jvm-main-process-child "SIGKILL")))

(defn start []
  (let [jar-path (madek.app.main.path-resolver/resolve-path "jvm-main.jar")]
    (.log js/console "starting jvm-main-process")
    (reset! jvm-main-process-child
            (.spawn child-process
                    "/usr/bin/java" (clj->js ["-jar" jar-path])))
    (.on @jvm-main-process-child "error" #(.log js/console (str "JVM-MAIN_PROC-ERR " %)))
    (.on @jvm-main-process-child "exit"
         (fn [code signal]
           (when (and code (not= 0 code))
             (throw (js/Error. (str "The jvm-main process exited abnormally with code " code))))))
    (.on (.-stdout @jvm-main-process-child) "data" #(.log js/console (str "JVM-MAIN_OUT " %)))
    (.on (.-stderr @jvm-main-process-child) "data" #(.log js/console (str "JVM-MAIN_ERR " %)))
    ;(js/setTimeout #(.log js/console @jvm-main-process-child) 1000)
    ))

(defn init [app]
  (case madek.app.main.env/env
    :dev nil ; (start)
    :prod (start))
  (.on app "quit" stop))
