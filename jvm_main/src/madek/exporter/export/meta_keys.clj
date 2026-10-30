(ns madek.exporter.export.meta-keys
  (:require
   [json-roa.client.core :as roa]
   [madek.exporter.request-log :as request-log]
   [madek.exporter.state :as state]))

(defn meta-keys_unmemoized []
  (->> (-> (request-log/get-root (state/connection-entry-point)
                                 :default-conn-opts (state/connection-http-options))
           (roa/relation :meta-keys)
           (roa/get {})
           roa/coll-seq)
       (map #(roa/get % {}))
       (map roa/data)
       (map (fn [k] [(:id k) k]))
       (into {})))

(def meta-keys (memoize meta-keys_unmemoized))

(defn reset-cache!
  "Drop memoized meta-keys (each download should log list HTTP again)."
  []
  (alter-var-root #'meta-keys (constantly (memoize meta-keys_unmemoized))))
