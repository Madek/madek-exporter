(ns madek.exporter.export.meta-keys
  (:require
   [json-roa.client.core :as roa]
   [madek.exporter.state :as state]))

(defn meta-keys_unmemoized []
  (->> (-> (roa/get-root (state/connection-entry-point)
                         :default-conn-opts (state/connection-http-options))
           (roa/relation :meta-keys)
           (roa/get {})
           roa/coll-seq)
       (map #(roa/get % {}))
       (map roa/data)
       (map (fn [k] [(:id k) k]))
       (into {})))

(def meta-keys (memoize meta-keys_unmemoized))
