(ns madek.exporter.request-log
  "Capture Madek HTTP requests during an active download for the UI logs panel."
  (:require
   [clj-http.client :as http-client]
   [clojure.string :as string]
   [json-roa.client.core :as roa]
   [madek.exporter.download-log-lines :as download-log-lines]
   [madek.exporter.state :as state]))

(def ^:private max-logs 2000)

(defn lines [] (download-log-lines/lines))

(defn clear!
  "Reset request logs for a new download run."
  []
  (download-log-lines/clear!)
  (swap! state/db assoc-in [:download :logs] [])
  (state/broadcast-download-logs!))

(defn- logging-enabled? []
  (boolean (get-in @state/db [:download :download-started])))

(defn- request-url [req]
  (or (:url req)
      (when (and (:scheme req) (:server-name req))
        (str (name (:scheme req)) "://" (:server-name req)
             (when-let [port (:server-port req)]
               (when-not (#{80 443} port) (str ":" port)))
             (:uri req)))
      ""))

(defn- status-label [status]
  (cond
    (nil? status) "ERR"
    :else (str status)))

(defn- append-line! [method url status]
  (when (logging-enabled?)
    (let [line (str (string/upper-case (name (or method :get)))
                    " " url " " (status-label status))]
      (download-log-lines/append! line :max-logs max-logs)
      (swap! state/db assoc-in [:download :logs] (download-log-lines/lines))
      (state/broadcast-download-logs!))))

(defn- log-from-exception! [req e]
  (append-line! (:method req)
                (request-url req)
                (or (:status (ex-data e))
                    (some-> e ex-data :object :status))))

(defn wrap-log
  "clj-http middleware: append `METHOD url status` for each request."
  [client]
  (fn
    ([req]
     (try
       (let [resp (client req)]
         (append-line! (:method req) (request-url req) (:status resp))
         resp)
       (catch Exception e
         (log-from-exception! req e)
         (throw e))))
    ([req respond raise]
     (client req
             (fn [resp]
               (append-line! (:method req) (request-url req) (:status resp))
               (respond resp))
             (fn [e]
               (log-from-exception! req e)
               (raise e))))))

(def middleware
  "default-middleware plus request logging (outermost)."
  (conj (vec http-client/default-middleware) wrap-log))

(defn get-root
  "Like `roa/get-root`, but records each HTTP call while a download is active."
  [url & {:keys [default-conn-opts]
          :or {default-conn-opts {}}}]
  (roa/get-root url
                :default-conn-opts default-conn-opts
                :default-middleware middleware))

(defn http-get
  "clj-http GET with the same request logging as ROA export calls."
  [url opts]
  (http-client/with-middleware middleware
    (http-client/get url opts)))
