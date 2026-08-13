(ns madek.exporter.export.progress
  (:require
   [madek.exporter.state :as state]
   [madek.exporter.utils :refer [deep-merge]]))

(defn set-item-progress!
  "Set :progress (0.0–1.0) and optional extra fields on a download item.
  No-op when cancel has been requested so late workers do not keep the UI alive."
  ([id progress]
   (set-item-progress! id progress nil))
  ([id progress extra]
   (when-not (get-in @state/db [:download :cancel-requested])
     (let [id (str id)
           progress (double (max 0.0 (min 1.0 progress)))
           patch (cond-> {:progress progress}
                   (map? extra) (merge extra))]
       (swap! state/db
              (fn [db]
                (deep-merge db {:download {:items {id patch}}})))))))
