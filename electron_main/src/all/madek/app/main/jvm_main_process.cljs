(ns madek.app.main.jvm-main-process
  (:require
    [madek.app.main.env :as env]

    [madek.app.main.file-logger :as file-log]

    [cljs.nodejs :as nodejs]
    ))

(def child-process (nodejs/require "child_process"))
(def path (nodejs/require "path"))
(def fs (nodejs/require "fs"))

(def jvm-main-process-child (atom nil))

(def min-java-major 21)

(defn parse-java-major [s]
  (some->> (re-find #"version \"([0-9]+)" s)
           second
           js/parseInt))

(defn java-compatible? [java-path]
  (try
    (let [result (.spawnSync child-process java-path
                             (clj->js ["-version"])
                             (clj->js {:encoding "utf8"}))
          output (str (or (.-stdout result) "") "\n" (or (.-stderr result) ""))
          major (parse-java-major output)]
      (and (number? major) (>= major min-java-major)))
    (catch :default _ false)))

(defn resolve-java-path []
  (let [env-java-path (some-> js/process .-env (aget "MADEK_EXPORTER_JAVA_PATH"))]
    (cond
      (seq env-java-path) env-java-path
      (= env/env :dev) "java"
      :else
      ;; Prod ships a pinned Temurin 21 under Resources/jre — trust it without
      ;; spawnSync java -version (slow under Rosetta / cold start).
      (let [bundled-java-path (.resolve path env/resources-dir "jre" "bin" "java")]
        (cond
          (.existsSync fs bundled-java-path) bundled-java-path
          (java-compatible? "java") "java"
          :else bundled-java-path)))))

(defn stop []
  (when @jvm-main-process-child
    (.log js/console "stopping jvm-main-process")
    (.kill @jvm-main-process-child "SIGKILL")))

(defn start []
  (let [jar-path (if (= env/env :prod)
                   (.resolve path env/resources-dir "jvm-main.jar")
                   (.resolve path env/app-dir "jvm-main.jar"))
        java-path (resolve-java-path)]
    (.log js/console "starting jvm-main-process"
          (clj->js {:java-path java-path
                    :jar-path jar-path}))
    (file-log/log! "INFO" "starting jvm-main-process" {:javaPath java-path :jarPath jar-path})
    (reset! jvm-main-process-child
            (.spawn child-process java-path
                    (clj->js ["-jar" jar-path
                              "server"
                              "-p" (str env/jvm-port)
                              "-s" (str env/jvm-password)])))
    (.on @jvm-main-process-child "error"
         (fn [err]
           (.log js/console (str "JVM-MAIN_PROC-ERR " err))
           (file-log/log! "ERROR" "JVM-MAIN_PROC-ERR" {:error (str err)})))
    (.on @jvm-main-process-child "exit"
         (fn [code signal]
           (when (and code (not= 0 code))
             (let [msg (str "The jvm-main process exited abnormally with code " code
                            (when signal (str " (signal " signal ")")))]
               (.error js/console msg)
               (file-log/log! "ERROR" "JVM-MAIN_EXIT_ABNORMAL" {:code code :signal signal})))
           (when (and (not code) signal)
             (file-log/log! "WARN" "JVM-MAIN_EXIT_SIGNAL" {:signal signal}))))
    (.on (.-stdout @jvm-main-process-child) "data"
         (fn [chunk]
           (.log js/console (str "JVM-MAIN_OUT " chunk))
           (file-log/log! "INFO" "JVM-MAIN_OUT" {:chunk (str chunk)})))
    (.on (.-stderr @jvm-main-process-child) "data"
         (fn [chunk]
           (.log js/console (str "JVM-MAIN_ERR " chunk))
           (file-log/log! "ERROR" "JVM-MAIN_ERR" {:chunk (str chunk)})))
    ;(js/setTimeout #(.log js/console @jvm-main-process-child) 1000)
    ))

(defn init [app]
  (case madek.app.main.env/env
    :dev (start)
    :prod (start))
  (.on app "quit" stop))
