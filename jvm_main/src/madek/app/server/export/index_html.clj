(ns madek.app.server.export.index-html
  (:require
    [madek.app.server.state :as state]
    [madek.app.server.utils :refer [deep-merge]]

    [hiccup.core :as hiccup]
    [hiccup.page :refer [html5]]
    [json-roa.client.core :as roa]
    [cheshire.core :as cheshire]
    [clojure.java.io :as io]
    [camel-snake-kebab.core :refer [->PascalCase]]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.thrown :as thrown]
    [logbug.debug :as debug :refer [I> I>>]]
    )

  (:import
    [java.io File]))

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

(defn title [media-resource]
  (str (-> media-resource :type name ->PascalCase )
       " "
       (-> media-resource :id)))

(defn url [media-resource]
  (let [url (str (-> @state/db :connection :url)
                 (case (:type media-resource)
                        :media-entry "/entries/"
                        :collection "/sets/")
                 (:id media-resource))]
    [:a
     {:href url}
     url]))

(defn html-generic-meta-datum-value [meta-datum]
  [:pre (cheshire/generate-string (or (:value meta-datum) (:values meta-datum)) {:pretty true})]
  )

(defn html-keywords-values [meta-datum]
  [:pre
   (->> (:values meta-datum)
        (map :term)
        (clojure.string/join ", "))])

(defn html-people-values [meta-datum]
  (-> (->> (:values meta-datum)
           (map #(dissoc % :date_of_birth :date_of_death :id)))
      (cheshire/generate-string {:pretty true})))

(defn html-meta-datum [meta-datum]
  (let [meta-key-id (:meta_key_id meta-datum)]
    [:div.meta-datum {:class (:meta_key_id meta-datum)}
     [:h3 (or (:label (get (meta-keys) meta-key-id nil)) meta-key-id)]
     (case (:type meta-datum)
       "MetaDatum::Keywords" (html-keywords-values meta-datum)
       ("MetaDatum::TextDate" "MetaDatum::Text") (:value meta-datum)
       "MetaDatum::People" (html-people-values meta-datum))]))

(defn html-meta-data [meta-data]
  [:div.meta-data
   [:h1 "Meta-Data"]
   (doall (for [meta-datum meta-data]
            (html-meta-datum meta-datum)))])

(defn html [media-resource meta-data]
  (html5
    [:head
     [:title (title media-resource)]
     [:meta {:charset "utf-8"}]
     [:body
      [:h2 (title media-resource)]
      [:p "URL: " (url media-resource)]
      (html-meta-data meta-data)]]))

(defn write [target-dir meta-data media-resource]
  (io/make-parents target-dir)
  (let [path (str target-dir File/separator "index.html")
        html (html media-resource meta-data)]
    (spit path html)))

;### Debug ####################################################################
;(debug/re-apply-last-argument #'write)
;(debug/debug-ns *ns*)
