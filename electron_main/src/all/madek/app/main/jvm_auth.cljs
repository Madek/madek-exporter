(ns madek.app.main.jvm-auth
  "Inject Basic Auth for localhost JVM traffic in the main process so the
  renderer never receives the JVM password."
  (:require
    [cljs.nodejs :as nodejs]
    [madek.app.main.env :as env]
    [madek.app.main.file-logger :as file-log]
    ))

(def Electron (nodejs/require "electron"))
(def Buffer (nodejs/require "buffer"))

(defonce ^:private installed?* (atom false))

(defn install!
  "Register a session webRequest hook that adds Authorization for the JVM port."
  []
  (when-not @installed?*
    (let [password (str env/jvm-password)
          port (str env/jvm-port)
          token (.toString (.from (.-Buffer Buffer) (str ":" password)) "base64")
          header (str "Basic " token)
          session (.-defaultSession (.-session Electron))
          filter (clj->js
                   {:urls [(str "http://127.0.0.1:" port "/*")
                           (str "http://localhost:" port "/*")
                           (str "ws://127.0.0.1:" port "/*")
                           (str "ws://localhost:" port "/*")
                           (str "http://[::1]:" port "/*")
                           (str "ws://[::1]:" port "/*")]})]
      (.onBeforeSendHeaders
        (.-webRequest session)
        filter
        (fn [details callback]
          (let [headers (js/Object.assign #js {} (.-requestHeaders details))]
            (aset headers "Authorization" header)
            (callback #js {:requestHeaders headers}))))
      (reset! installed?* true)
      (file-log/log! "INFO" "JVM Basic Auth header injection installed"
                     {:port port}))))
