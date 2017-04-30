(ns madek.jvm-main-process
  (:require
    [madek.env]
    [madek.path-resolver]

    [cljs.nodejs :as nodejs]
    ))

(def child-process (nodejs/require "child_process"))

(def jvm-main-process-child (atom nil))

(defn stop []
  (when @jvm-main-process-child
    (.log js/console "stopping jvm-main-process")
    (.kill @jvm-main-process-child "SIGKILL")))

(defn start []
  (let [jar-path (madek.path-resolver/resolve-path "jvm-main.jar")]
    (.log js/console "starting jvm-main-process")
    (reset! jvm-main-process-child
            (.spawn child-process
                    "/usr/bin/java" (clj->js ["-jar" jar-path])))
    (.on @jvm-main-process-child "error" #(.log js/console (str "JVM-MAIN_PROC-ERR " %)))
    (.on (.-stdout @jvm-main-process-child) "data" #(.log js/console (str "JVM-MAIN_OUT " %)))
    (.on (.-stderr @jvm-main-process-child) "data" #(.log js/console (str "JVM-MAIN_ERR " %)))
    ;(js/setTimeout #(.log js/console @jvm-main-process-child) 1000)
    ))

(defn init [app]
  (case madek.env/env
    :dev nil; (start)
    :prod (start))
  (.on app "quit" stop))