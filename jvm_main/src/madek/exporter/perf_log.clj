(ns madek.exporter.perf-log
  "In-memory request timings for the Debug → Performance tab.
  `:madek` entries are outgoing calls to Madek, `:local` entries are calls
  from the UI to this JVM."
  (:require
   [clj-http.client :as http-client]
   [clojure.string :as string])
  (:import
   [java.time Instant]))

(def ^:private max-entries 1000)

(defonce ^:private entries* (atom []))

(defonce ^:private seq-no* (atom 0))

(defn entries [] @entries*)

(defn clear! [] (reset! entries* []))

(defn- elapsed-ms [start-ns]
  (quot (- (System/nanoTime) start-ns) 1000000))

(defn- record! [kind method url status start-ns]
  (let [entry {:id (swap! seq-no* inc)
               :kind kind
               :at (str (Instant/now))
               :method (string/upper-case (name (or method :get)))
               :url url
               :status status
               :ms (elapsed-ms start-ns)
               :thread (.getName (Thread/currentThread))}]
    (swap! entries*
           (fn [es]
             (let [v (conj es entry)]
               (if (> (count v) max-entries)
                 (subvec v (- (count v) max-entries))
                 v))))))

(defn- request-url [req]
  (or (:url req)
      (when (and (:scheme req) (:server-name req))
        (str (name (:scheme req)) "://" (:server-name req)
             (when-let [port (:server-port req)]
               (when-not (#{80 443} port) (str ":" port)))
             (:uri req)))
      ""))

(defn- error-status [e]
  (or (:status (ex-data e))
      (some-> e ex-data :object :status)
      "ERR"))

(defn wrap-perf
  "clj-http middleware: record method, url, status and duration."
  [client]
  (fn
    ([req]
     (let [start (System/nanoTime)]
       (try
         (let [resp (client req)]
           (record! :madek (:method req) (request-url req) (:status resp) start)
           resp)
         (catch Exception e
           (record! :madek (:method req) (request-url req) (error-status e) start)
           (throw e)))))
    ([req respond raise]
     (let [start (System/nanoTime)]
       (client req
               (fn [resp]
                 (record! :madek (:method req) (request-url req) (:status resp) start)
                 (respond resp))
               (fn [e]
                 (record! :madek (:method req) (request-url req) (error-status e) start)
                 (raise e)))))))

(def middleware
  "clj-http default-middleware plus timing (outermost)."
  (conj (vec http-client/default-middleware) wrap-perf))

(defn- skip-local? [uri]
  (or (string/starts-with? uri "/chsk")
      (string/starts-with? uri "/debug/performance")
      (re-find #"\.(js|css|map|png|svg|ico|woff2?|ttf|html)$" uri)))

(defn wrap-ring
  "Ring middleware: record UI → JVM requests (not websocket, assets or this
  tab's own polling)."
  [handler]
  (fn [request]
    (let [uri (or (:uri request) "")]
      (if (skip-local? uri)
        (handler request)
        (let [start (System/nanoTime)]
          (try
            (let [resp (handler request)]
              (record! :local (:request-method request) uri (:status resp) start)
              resp)
            (catch Throwable e
              (record! :local (:request-method request) uri "ERR" start)
              (throw e))))))))
