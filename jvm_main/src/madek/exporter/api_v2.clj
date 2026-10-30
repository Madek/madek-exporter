(ns madek.exporter.api-v2
  "Plain HTTP client for Madek `/api-v2`.

  Paths follow `tmp/madek-api-v2` user routes (the OpenAPI source). Trailing
  slashes match the route table: list and nested collection routes keep them,
  single-resource routes and preview data-streams do not.

  List responses are either a named collection (`{:media_entries [...]}`) or,
  when `page` or `size` is set, `{:data [...] :pagination {...}}`. Default page
  size on the server is 10 once pagination is turned on; this client asks for
  `page-size` and walks `total_pages`."
  (:require
   [cheshire.core :as cheshire]
   [clojure.string :as string]
   [madek.exporter.request-log :as request-log]
   [madek.exporter.utils :as utils]
   [taoensso.timbre :refer [debug warn]]))

(def page-size
  "Items requested per list page. The server paginates only when page or size
  is present, and then defaults to 10 if the other param is omitted."
  100)

(def ^:private max-pages 10000)

(def person-keys
  "Fields the v1 exporter keeps on an enriched person."
  [:external_uris :first_name :id :institution :institutional_id
   :last_name :pseudonym :subtype])

;;; paths #####################################################################

(defn media-entry-path [id] (str "/media-entries/" id))
(defn collection-path [id] (str "/collections/" id))

(defn meta-data-path
  "List meta-data. `meta_keys` is a JSON array string, same as v1."
  [entity-type id]
  (str (case entity-type
         :media-entry (media-entry-path id)
         :collection (collection-path id))
       "/meta-data/"))

(defn meta-data-related-path
  "Meta-data plus keyword / people rows in one response."
  [entity-type id]
  (str (case entity-type
         :media-entry (media-entry-path id)
         :collection (collection-path id))
       "/meta-data-related/"))

(defn media-file-path [media-entry-id]
  (str "/media-entries/" media-entry-id "/media-files/"))

(defn media-file-stream-path [media-file-id]
  (str "/media-files/" media-file-id "/data-stream/"))

(defn previews-path [media-entry-id]
  (str "/media-entries/" media-entry-id "/previews/"))

(defn preview-path [preview-id]
  (str "/previews/" preview-id))

(defn preview-stream-path
  "No trailing slash — the v2 route is `/previews/:id/data-stream`."
  [preview-id]
  (str "/previews/" preview-id "/data-stream"))

(defn person-path [id] (str "/people/" id))
(defn role-path [id] (str "/roles/" id))

;;; query params ##############################################################

(defn child-media-entries-query
  "Members of a set. Pagination is added by `fetch-pages`.

  v1: `GET /media-entries/?collection_id&me_get_full_size=true`
  (or `public_get_full_size` when anonymous).

  v2 accepts the same flags. `me_*` must be boolean true (the string `\"true\"`
  is coerced). `collection_id` joins `collection_media_entry_arcs`, so the
  arcs endpoint is not required for the walk. v1 ROA `coll-seq` hid paging."
  [collection-id authenticated?]
  (merge {:collection_id (str collection-id)}
         (if authenticated?
           {:me_get_full_size true}
           {:public_get_full_size true})))

(defn child-collections-query
  "Child sets. Pagination is added by `fetch-pages`.

  v1 reads the ROA `:collections` relation on the parent with
  `me_get_metadata_and_previews=true` (or `public_get_metadata_and_previews`).

  v2: `GET /collections/?collection_id={parent}` plus the same flag.
  `collection_id` joins `collection_collection_arcs`."
  [collection-id authenticated?]
  (merge {:collection_id (str collection-id)}
         (if authenticated?
           {:me_get_metadata_and_previews true}
           {:public_get_metadata_and_previews true})))

;;; response shapes ###########################################################

(defn ok-status? [status]
  (and (integer? status) (>= status 200) (< status 300)))

(defn unwrap-list
  "Items from a v2 list body.

  `keys*` is the preference order, usually `[:data :media_entries]` so a
  paginated body wins over a named collection."
  [body keys*]
  (cond
    (sequential? body) (vec body)
    (map? body) (or (some (fn [k]
                            (when (sequential? (get body k))
                              (vec (get body k))))
                          keys*)
                    [])
    :else []))

(defn paged-items
  "`fetch-page` takes a 1-based page number and returns a response body.
  Follows `:pagination :total_pages` when present; otherwise one page."
  [fetch-page list-keys]
  (loop [page 1
         acc []]
    (when (> page max-pages)
      (throw (ex-info "api-v2 pagination exceeded the page limit"
                      {:page page :max-pages max-pages})))
    (let [body (fetch-page page)
          items (unwrap-list body list-keys)
          total-pages (get-in body [:pagination :total_pages])]
      (if (and (integer? total-pages) (< page total-pages))
        (recur (inc page) (into acc items))
        (into acc items)))))

(defn locale-label
  "Single label string from a v2 multilingual map. v1 ROA already resolved the
  server default locale into `:label`. Prefer German, then English."
  [labels]
  (cond
    (string? labels) labels
    (map? labels) (or (some-> (or (:de labels) (get labels "de")) str not-empty)
                      (some-> (or (:en labels) (get labels "en")) str not-empty)
                      (->> labels vals (keep #(when (string? %) (not-empty %))) first))
    :else nil))

(defn- compact [m]
  (into {} (remove (comp nil? val) m)))

(defn- json-value [raw]
  (cond
    (string? raw) (try
                    (cheshire/parse-string raw true)
                    (catch Exception _ raw))
    :else raw))

(defn- scalar-value [meta-data]
  (case (:type meta-data)
    ("MetaDatum::Text" "MetaDatum::TextDate") (:string meta-data)
    "MetaDatum::JSON" (json-value (:json meta-data))
    (:string meta-data)))

(defn- person-values
  "Match v1 ROA `values`: flat person docs (id = person id) first, then
  meta_data_people wrappers with nested `:person`."
  [md-people people role-fn]
  (let [by-id (into {} (map (fn [p] [(str (:id p)) p]) people))
        nested (mapv (fn [mdp]
                       (let [person (get by-id (str (:person_id mdp)))
                             role-id (:role_id mdp)
                             role (when role-id (role-fn role-id))]
                         (cond-> mdp
                           person (-> (dissoc :person_id)
                                      (assoc :person (select-keys person person-keys)))
                           role (-> (dissoc :role_id)
                                    (assoc :role (select-keys role [:id :labels]))))))
                     md-people)]
    (into (vec people) nested)))

(defn export-meta-datum
  "One `meta-data-related` element → the map written into `meta-data.json`.

  v2 stores text in `:string` and JSON in `:json`. v1 ROA exposes both as
  `:value`. People and keywords become `:values` (flat person + nested
  person wrapper, like v1; role nested when `role-fn` returns one)."
  [item role-fn label-fn]
  (let [md (or (:meta_data item) item)
        meta-key-id (:meta_key_id md)
        type (:type md)
        base (compact (select-keys md [:id :meta_key_id :type
                                       :media_entry_id :collection_id]))
        label (when label-fn (label-fn meta-key-id))
        with-label (cond-> base label (assoc :label label))]
    (case type
      ("MetaDatum::People")
      (assoc with-label :values (person-values (or (:md_people item) [])
                                               (or (:people item) [])
                                               (or role-fn (constantly nil))))

      ("MetaDatum::Keywords")
      (assoc with-label :values (vec (or (:keywords item) [])))

      (assoc with-label :value (scalar-value md)))))

(defn export-meta-data
  [related-items role-fn label-fn]
  (mapv #(export-meta-datum % role-fn label-fn) related-items))

(defn related-items
  "The related endpoint returns a JSON array. An empty or missing body is
  no meta-data, not an error."
  [body]
  (cond
    (nil? body) []
    (sequential? body) (vec body)
    :else []))

;;; HTTP ######################################################################

(defn join-url [base path]
  (str (string/replace (str base) #"/+$" "")
       (if (string/starts-with? (str path) "/") path (str "/" path))))

(defn- error-body [body]
  (let [text (cond
               (string? body) body
               (nil? body) ""
               :else (str body))
        trimmed (string/replace text #"\s+" " ")]
    (if (> (count trimmed) 180)
      (str (subs trimmed 0 177) "...")
      trimmed)))

(defn- throw-http! [path response]
  (let [status (:status response)]
    (throw (ex-info (str "api-v2 " path " failed"
                         (when status (str " (HTTP " status ")"))
                         (when-let [msg (not-empty (error-body (:body response)))]
                           (str ": " msg)))
                    {:status status :path path}))))

(defn- parse-json-body [body]
  (cond
    (nil? body) nil
    (string? body) (when (not-empty (string/trim body))
                     (cheshire/parse-string body true))
    :else body))

(defn get-json!
  "GET JSON. Throws on a non-2xx status. `query` may be nil."
  [base-url path http-opts query]
  (let [url (join-url base-url path)
        opts (-> (or http-opts {})
                 (update :headers merge {"accept" "application/json"})
                 (merge {:as :text
                         :throw-exceptions false
                         :coerce :false}))
        opts (if (seq query) (assoc opts :query-params query) opts)
        _ (debug "api-v2 GET" url query)
        response (request-log/http-get url opts)]
    (if (ok-status? (:status response))
      (parse-json-body (:body response))
      (throw-http! path response))))

(defn try-get-json
  "Like `get-json!`, but returns nil on 404."
  [base-url path http-opts query]
  (try
    (get-json! base-url path http-opts query)
    (catch clojure.lang.ExceptionInfo e
      (if (= 404 (:status (ex-data e)))
        nil
        (throw e)))))

(defn get-stream!
  "GET a binary stream. Caller must close `:body` (see `save-http-stream!`)."
  [base-url path http-opts]
  (let [url (join-url base-url path)
        response (request-log/http-get url (merge (or http-opts {})
                                                  {:as :stream
                                                   :throw-exceptions false}))]
    (if (ok-status? (:status response))
      response
      (do
        (when (instance? java.io.InputStream (:body response))
          (.close ^java.io.InputStream (:body response)))
        (throw-http! path response)))))

(defn fetch-pages
  [base-url path http-opts query list-keys]
  (paged-items (fn [page]
                 (get-json! base-url path http-opts
                            (assoc (or query {}) :page page :size page-size)))
               list-keys))

(defn fetch-title
  "Title used on the download form. Confirms the entity exists, then reads
  `madek_core:title` from the meta-data list (`:string`, not ROA `:value`)."
  [base-url http-opts entity-type id]
  (get-json! base-url
             (case entity-type
               :media-entry (media-entry-path id)
               :collection (collection-path id))
             http-opts
             nil)
  (let [body (get-json! base-url (meta-data-path entity-type id) http-opts
                        {:meta_keys (cheshire/generate-string ["madek_core:title"])})]
    (:string (some #(when (= "madek_core:title" (:meta_key_id %)) %)
                   (:meta_data body)))))

(defn- with-label [entity]
  (assoc entity :label (or (:label entity)
                           (locale-label (:labels entity))
                           (:id entity))))

(defn vocabularies
  "Vocabulary list for the prefix picker and the schema file. Each map has a
  string `:label`."
  [base-url http-opts]
  (->> (fetch-pages base-url "/vocabularies/" http-opts nil [:data :vocabularies])
       (map with-label)
       (sort-by #(or (:position %) 0))
       vec))

(defn meta-keys
  "All meta-keys visible to the caller, with a string `:label`."
  [base-url http-opts]
  (->> (fetch-pages base-url "/meta-keys/" http-opts nil [:data :meta-keys])
       (map with-label)
       vec))

(defn meta-keys-for-vocabulary
  [base-url http-opts vocabulary-id]
  (->> (meta-keys base-url http-opts)
       (filter #(= (str vocabulary-id) (str (:vocabulary_id %))))
       vec))

(defn authenticated?
  [http-opts]
  (utils/authenticated-http-options? http-opts))

(defn child-ids
  [base-url http-opts path query list-keys]
  (->> (fetch-pages base-url path http-opts query list-keys)
       (keep :id)
       (map str)
       vec))
