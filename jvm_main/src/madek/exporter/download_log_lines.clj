(ns madek.exporter.download-log-lines
  "In-memory HTTP log lines for the export UI (no dependency on state).")

(defonce ^:private log-lines (atom []))

(defn lines []
  @log-lines)

(defn append!
  [line & {:keys [max-logs] :or {max-logs 2000}}]
  (swap! log-lines
         (fn [logs]
           (let [v (conj (vec logs) line)]
             (if (> (count v) max-logs)
               (subvec v (- (count v) max-logs))
               v)))))

(defn clear! []
  (reset! log-lines []))

(defn attach-to-db [db]
  (assoc-in db [:download :logs] (vec @log-lines)))
