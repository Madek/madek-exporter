(ns madek.exporter.export.control
  "Shared cancel / generation guards for export workers.
  Kept separate from export.clj so files.clj can use it without a cycle."
  (:require
   [madek.exporter.state :as state]))

(def ^:dynamic *download-generation*
  "Generation captured when a download run starts. Bound on the download
  future and re-bound on media-entry worker threads."
  nil)

(defn current-generation []
  (or (get-in @state/db [:download :download-generation]) 0))

(defn bump-generation!
  "Invalidate in-flight workers belonging to a previous run. Returns the new generation."
  []
  (-> (swap! state/db update-in [:download :download-generation] (fnil inc 0))
      (get-in [:download :download-generation])))

(defn cancel-requested? []
  (boolean (get-in @state/db [:download :cancel-requested])))

(defn ensure-not-cancelled!
  "Throw when the user cancelled or this worker's generation was superseded."
  []
  (when (or (cancel-requested?)
            (and (some? *download-generation*)
                 (not= *download-generation* (current-generation))))
    (throw (ex-info "Download cancelled by user"
                    {:type :download-cancelled}))))
