(ns madek.exporter.server.prefix-options
  "Step-2 prefix meta-key catalog walk (ROA /api or REST /api-v2)."
  (:require
   [clojure.string :as string]
   [clojure.tools.logging :as logging]
   [json-roa.client.core :as roa]
   [logbug.catcher :as catcher]
   [madek.exporter.api :as api]
   [madek.exporter.api-v2 :as api-v2]
   [madek.exporter.export.files :as export-files]
   [madek.exporter.perf-log :as perf-log]
   [madek.exporter.state :as state]
   [madek.exporter.utils :refer [presence]]
   [ring.util.response :refer [response]]
   [taoensso.timbre :refer [info]])
  (:import
   [java.util.concurrent ExecutionException ExecutorService Future Executors]))

(defonce generation (atom 0))

(defn abort!
  "Invalidate an in-flight prefix-options walk so it stops calling Madek.
  Returns the new generation."
  []
  (swap! generation inc))

(defn aborted? [gen]
  (not= gen @generation))

(defn- throw-unless-current! [gen]
  (when (aborted? gen)
    (throw (ex-info "Prefix options aborted" {:type :prefix-options-aborted}))))

(defn- client-error-body [e]
  (let [msg (or (ex-message e) "Internal error")
        typ (:type (ex-data e))]
    (logging/error e "prefix-options failed")
    (cond-> {:message msg}
      typ (assoc :type typ))))

(defn- api-root []
  (roa/get-root (state/connection-entry-point)
                :default-conn-opts (state/connection-http-options)
                :default-middleware perf-log/middleware))

(def ^:private concurrency 6)

(defonce ^:private pool*
  (atom (Executors/newFixedThreadPool (int concurrency))))

(defn- parallel-mapv
  [n f items]
  (if (empty? items)
    []
    (let [^ExecutorService pool @pool*
          futs (mapv (fn [item]
                       (.submit pool ^Callable (fn [] (f item))))
                     items)]
      (try
        (mapv #(.get ^Future %) futs)
        (catch ExecutionException e
          (doseq [^Future fut futs] (.cancel fut true))
          (throw (or (.getCause e) e)))))))

(defn- roa-collection-data
  ([gen resource relation-kw]
   (roa-collection-data gen resource relation-kw {}))
  ([gen resource relation-kw query]
   (throw-unless-current! gen)
   (let [rels (-> resource
                  (roa/relation relation-kw)
                  (roa/get query)
                  roa/coll-seq
                  vec)]
     (parallel-mapv concurrency
                    (fn [rel]
                      (throw-unless-current! gen)
                      (roa/data (roa/get rel {})))
                    rels))))

(defn- vocabulary-id-of [meta-key]
  (or (presence (:vocabulary_id meta-key))
      (some-> (:id meta-key) str (string/split #":" 2) first)))

(defn- prefix-folder-name [entity-id value]
  (if-let [v (presence value)]
    (str (export-files/useableFileName v) "_" entity-id)
    (str "_" entity-id)))

(defn- rows [id vocabs values meta-keys]
  (->> meta-keys
       (filter #(= "MetaDatum::Text" (:meta_datum_object_type %)))
       (map (fn [mk]
              (let [vid (vocabulary-id-of mk)
                    voc (get vocabs vid)
                    value (get values (:id mk))]
                {:vocabulary_id vid
                 :vocabulary_label (or (:label voc) vid)
                 :vocabulary_position (or (:position voc) 0)
                 :meta_key_id (:id mk)
                 :meta_key_label (or (:label mk) (:id mk))
                 :meta_key_position (or (:position mk) 0)
                 :value value
                 :folder_name (prefix-folder-name id value)})))
       (sort-by (juxt :vocabulary_position
                      :meta_key_position
                      :vocabulary_label
                      :meta_key_label))
       vec))

(defn- via-api [gen id entity-type title]
  (let [root (api-root)
        vocabs (->> (roa-collection-data gen root :vocabularies)
                    (map (juxt :id identity))
                    (into {}))
        entity (do (throw-unless-current! gen)
                   (-> root
                       (roa/relation (keyword entity-type))
                       (roa/get {:id id})))
        values (->> (roa-collection-data gen entity :meta-data)
                    (filter #(= "MetaDatum::Text" (:type %)))
                    (map (juxt :meta_key_id :value))
                    (into {}))
        options (rows id vocabs values
                      (roa-collection-data gen root :meta-keys))]
    (throw-unless-current! gen)
    (response {:entity_id id
               :entity_title title
               :options options})))

(defn- via-api-v2 [gen id entity-type title]
  (let [base-url (api/entry-point :api-v2)
        http-opts (state/connection-http-options)
        _ (throw-unless-current! gen)
        vocabs (->> (api-v2/vocabularies base-url http-opts)
                    (map (juxt :id identity))
                    (into {}))
        _ (throw-unless-current! gen)
        md-body (api-v2/get-json! base-url
                                  (api-v2/meta-data-path entity-type id)
                                  http-opts
                                  nil)
        values (->> (or (:meta_data md-body) [])
                    (filter #(= "MetaDatum::Text" (:type %)))
                    (map (juxt :meta_key_id :string))
                    (into {}))
        _ (throw-unless-current! gen)
        options (rows id vocabs values
                      (api-v2/meta-keys base-url http-opts))]
    (throw-unless-current! gen)
    (response {:entity_id id
               :entity_title title
               :options options})))

(defn- api-mode [request]
  (api/normalize-mode
   (or (get-in request [:params :api-mode])
       (get-in request [:query-params :api-mode])
       (api/current-mode))))

(defn handler
  "All text meta-keys from the Madek catalog, joined with the step-1 entity's
  values. API selection: api → `/api`, api-v2 → `/api-v2`, both → `/api`."
  [request]
  (catcher/snatch
   {:return-fn (fn [e]
                 (if (= :prefix-options-aborted (:type (ex-data e)))
                   {:status 499 :body {:aborted true}}
                   {:status 500 :body (client-error-body e)}))}
   (let [{id :uuid entity-type :type title :title} (-> @state/db :download :entity)
         mode (api-mode request)]
     (if-not id
       {:status 409 :body {:message "No export entity selected (step 1)."}}
       (let [gen (abort!)]
         (info "prefix-options" {:api-mode mode
                                 :entity-id id
                                 :entity-type entity-type})
         (case mode
           :api-v2 (via-api-v2 gen id entity-type title)
           (via-api gen id entity-type title)))))))
