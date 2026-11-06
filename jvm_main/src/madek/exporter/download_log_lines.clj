(ns madek.exporter.download-log-lines
  "In-memory HTTP log lines for the export UI (no dependency on state).")

(defonce ^:private log-lines (atom []))
(defonce ^:private request-count (atom 0))

(defn lines []
  @log-lines)

(defn request-count []
  @request-count)

(defn append!
  [line & {:keys [max-logs] :or {max-logs 2000}}]
  (swap! request-count inc)
  (swap! log-lines
         (fn [logs]
           (let [v (conj (vec logs) line)]
             (if (> (count v) max-logs)
               (subvec v (- (count v) max-logs))
               v)))))

(defn clear! []
  (reset! log-lines [])
  (reset! request-count 0))

(defn attach-to-db [db]
  (-> db
      (assoc-in [:download :logs] (vec @log-lines))
      (assoc-in [:download :request-count] @request-count)))
