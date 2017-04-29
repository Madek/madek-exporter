(ns madek.app.server.export.meta-data
  (:require
    [madek.app.server.state :as state]
    [json-roa.client.core :as roa]
    [madek.app.server.utils :refer [deep-merge]]

    [cheshire.core :as cheshire]
    [clojure.java.io :as io]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.thrown :as thrown]
    [logbug.debug :as debug :refer [I> I>>]]
    )

  (:import
    [java.io File]
    )

  )


;### meta data ################################################################

(defn get-meta-data-for-md-relation-type [md]
  (-> md (roa/get {})
      roa/data))

(defn get-collection-meta-datum-values [meta-datum]
  (merge (-> meta-datum roa/data
             (dissoc :value))
         {:values (->> meta-datum
                       roa/coll-seq
                       (map get-meta-data-for-md-relation-type))}))

(defn get-scalar-meta-datum-value [meta-datum]
  (-> meta-datum
      roa/data))

(defn get-metadata [media-resource]
  (->> (-> media-resource
           (roa/relation :meta-data)
           (roa/get {})
           roa/coll-seq)
       (map #(roa/get % {}))
       (map (fn [meta-datum]
              (case (-> meta-datum roa/data :type)
                ("MetaDatum::Text" "TextDate") (get-scalar-meta-datum-value meta-datum)
                (get-collection-meta-datum-values meta-datum)
                )))))

(defn title [meta-data]
  (:value (some (fn [meta-datum]
                  (and (=  "madek_core:title" (:meta_key_id meta-datum))
                       meta-datum))
                meta-data)))

(defn write-meta-data [target-dir meta-data item-id]
  (let [path (str target-dir File/separator "meta-data.json")]
    (io/make-parents path)
    (spit path (cheshire/generate-string meta-data {:pretty true}))
    (swap! state/db
           (fn [db params]
             (deep-merge db params))
           {:download
            {:items
             {item-id
              {:title (title meta-data)
               :meta-data
               {:path path
                :data meta-data }
               }}}})))

;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'ring.middleware.resource)
;(debug/debug-ns *ns*)

