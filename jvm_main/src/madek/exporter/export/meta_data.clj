(ns madek.exporter.export.meta-data
  (:require
   [cheshire.core :as cheshire]
   [clojure.java.io :as io]
   [json-roa.client.core :as roa]
   [logbug.catcher :as catcher]
   [logbug.debug :as debug :refer [I> I>>]]
   [logbug.thrown :as thrown]
   [madek.exporter.state :as state]
   [madek.exporter.utils :refer [deep-merge]]
   [taoensso.timbre :as timbre :refer [info debug warn error spy]])
  (:import
   [java.io File]))

;### meta data ################################################################

(defn replace-person-id-with-person-data
  "enrichtes the given data with person information iff it has a person_id key"
  [{person-id :person_id :as data} roa-conn-opts]
  (if-not person-id
    data
    (let [person (-> ; build some fake person-roa-data since this is missing as an relation
                     ; then use this to get the person data
                  {:name "Person"
                   :href (str "/api/people/" person-id)
                   :relations {}
                   :roa-conn-opts roa-conn-opts}
                  (roa/get {}) ; get it
                  roa/data
                  (select-keys [:external_uris
                                :first_name
                                :id
                                :institution
                                :institutional_id
                                :last_name
                                :pseudonym
                                :subtype]))]
      (-> data
          (dissoc :person_id)
          (assoc :person person)))))

(defn replace-role-id-with-role-data
  "enrichtes the given data with role information iff it has a role_id key"
  [{role-id :role_id :as data} roa-conn-opts]
  (if-not role-id
    data
    (let [role (-> ; build some fake role-roa-data since this is missing as an relation
                   ; then use this to get the role data
                {:name "Role"
                 :href (str "/api/roles/" role-id)
                 :relations {}
                 :roa-conn-opts roa-conn-opts}
                (roa/get {}) ; get it
                roa/data
                (select-keys [:id
                              :labels]))]
      (-> data
          (dissoc :role_id)
          (assoc :role role)))))

(defn get-meta-data-for-md-relation-type [md]
  (-> md
      (roa/get {})
      roa/data
      (replace-person-id-with-person-data (:roa-conn-opts md))
      (replace-role-id-with-role-data (:roa-conn-opts md))))

(defn get-collection-meta-datum-values [meta-datum]
  (merge (-> meta-datum roa/data
             (dissoc :value))
         {:values (->> meta-datum
                       roa/coll-seq
                       (map get-meta-data-for-md-relation-type))}))

(defn get-scalar-meta-datum-value [meta-datum]
  (-> meta-datum
      roa/data))

(defn meta-data_unmemoized [media-resource]
  (->> (-> media-resource
           (roa/relation :meta-data)
           (roa/get {})
           roa/coll-seq)
       (map #(roa/get % {}))
       (map (fn [meta-datum]
              (case (-> meta-datum roa/data :type)
                ("MetaDatum::Text" "MetaDatum::TextDate") (get-scalar-meta-datum-value meta-datum)
                (get-collection-meta-datum-values meta-datum))))))

(def meta-data (memoize meta-data_unmemoized))

(defn title [meta-data]
  (:value (some (fn [meta-datum]
                  (and (= "madek_core:title" (:meta_key_id meta-datum))
                       meta-datum))
                meta-data)))

(defn write-meta-data [target-dir meta-data item-id prefix-path]
  (let [content (cheshire/generate-string meta-data {:pretty true})]
    (doseq [path [(str target-dir File/separator "meta-data.json")
                  (str target-dir File/separator prefix-path "_meta-data.json")]]
      (io/make-parents path)
      (spit path content)
      (swap! state/db
             (fn [db params]
               (deep-merge db params))
             {:download
              {:items
               {item-id
                {:title (title meta-data)
                 :meta-data
                 {:path path
                  :data meta-data}}}}}))))

;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'ring.middleware.resource)
;(debug/debug-ns *ns*)
