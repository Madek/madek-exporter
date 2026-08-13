(ns madek.exporter.export.structure
  (:require
   [clojure.string :as string]
   [json-roa.client.core :as roa]
   [madek.exporter.export.files :as files]
   [madek.exporter.utils :refer [presence]]))

(def new-export-structure "new-export-structure")
(def legacy-export-structure "legacy-export-structure")

(defn normalize [v]
  (case (str v)
    "legacy-export-structure" legacy-export-structure
    "new-export-structure" new-export-structure
    new-export-structure))

(defn write-prefixed-artifacts? [strategy]
  (= (normalize strategy) legacy-export-structure))

(defn legacy-usable-filename [s]
  (.replaceAll (str s) "[^a-zA-Z0-9 ]" ""))

(defn slugify [s]
  (-> (str s)
      string/lower-case
      (string/replace #"\s+" "-")
      (string/replace #"[^a-z0-9_-]" "")
      (string/replace #"-+" "-")
      (string/replace #"^-+|-+$" "")))

(defn- legacy-dir-name [prefix-meta-key media-resource]
  (let [prefix-part-one (if-not (presence prefix-meta-key) ""
                                (if-let [mk-value (files/get-prefix
                                                   prefix-meta-key media-resource)]
                                  (str (legacy-usable-filename mk-value) "_") ""))]
    (str prefix-part-one (-> media-resource roa/data :id))))

(defn- new-dir-name [prefix-meta-key media-resource]
  (let [id (str (-> media-resource roa/data :id))]
    (if-not (presence prefix-meta-key)
      id
      (let [slug (slugify (files/get-prefix prefix-meta-key media-resource))]
        (if (string/blank? slug)
          id
          (str id "_" slug))))))

(defn dir-name [strategy prefix-meta-key media-resource]
  (case (normalize strategy)
    "legacy-export-structure" (legacy-dir-name prefix-meta-key media-resource)
    (new-dir-name prefix-meta-key media-resource)))
