(ns madek.exporter.export.api-v2
  "Export one entity through `/api-v2` into the same tree the JSON-ROA exporter
  writes: entity folder, `meta-data.json`, `index.html`, originals, previews,
  and `meta-data_schema.json`."
  (:require
   [cheshire.core :as cheshire]
   [clojure.java.io :as io]
   [logbug.catcher :as catcher]
   [madek.exporter.api :as api]
   [madek.exporter.api-v2 :as api-v2]
   [madek.exporter.export :as export]
   [madek.exporter.export.files :as files]
   [madek.exporter.export.index-html :as index-html]
   [madek.exporter.export.meta-data :as meta-data]
   [madek.exporter.export.progress :as progress]
   [madek.exporter.utils :refer [presence]]
   [taoensso.timbre :refer [warn]])
  (:import
   [java.io File]))

(defn context
  "Per-run cache. Meta-keys and roles are shared across parallel media entries."
  [base-url http-opts]
  {:base-url base-url
   :http-opts http-opts
   :meta-keys (atom nil)
   :roles (atom {})})

(defn- meta-keys-by-id! [ctx]
  (if-let [cached @(:meta-keys ctx)]
    cached
    (locking (:meta-keys ctx)
      (or @(:meta-keys ctx)
          (let [indexed (->> (api-v2/meta-keys (:base-url ctx) (:http-opts ctx))
                             (map (fn [k] [(:id k) k]))
                             (into {}))]
            (reset! (:meta-keys ctx) indexed)
            indexed)))))

(defn- label-fn [ctx]
  (let [keys (meta-keys-by-id! ctx)]
    (fn [meta-key-id]
      (or (:label (get keys meta-key-id))
          (api-v2/locale-label (:labels (get keys meta-key-id)))
          meta-key-id))))

(defn- role! [ctx role-id]
  (let [id (str role-id)]
    (if-let [cached (get @(:roles ctx) id)]
      cached
      (let [role (api-v2/try-get-json (:base-url ctx)
                                      (api-v2/role-path id)
                                      (:http-opts ctx)
                                      nil)]
        (when role
          (swap! (:roles ctx) assoc id role))
        role))))

(defn- folder-name [prefix-meta-key meta-data resource-id]
  (let [prefix (when (presence prefix-meta-key)
                 (some (fn [md]
                         (when (= (str prefix-meta-key) (str (:meta_key_id md)))
                           (:value md)))
                       meta-data))
        prefix-part (if (presence (some-> prefix str))
                      (str (files/useableFileName (str prefix)) "_")
                      "")]
    (str prefix-part resource-id)))

(defn- load-meta-data [ctx entity-type id]
  (let [body (api-v2/try-get-json (:base-url ctx)
                                  (api-v2/meta-data-related-path entity-type id)
                                  (:http-opts ctx)
                                  nil)]
    (api-v2/export-meta-data (api-v2/related-items body)
                             #(role! ctx %)
                             (label-fn ctx))))

(defn- download-previews [ctx target-dir item-id media-entry-id media-file-id preview-summaries]
  (let [item-id (str item-id)
        previews-dir (str target-dir File/separator "previews")
        summaries (vec preview-summaries)
        preview-n (count summaries)]
    (if (zero? preview-n)
      (progress/set-item-progress! item-id 0.95
                                   {:progress-label "preview"
                                    :file-progress 1.0})
      (doseq [[idx summary] (map-indexed vector summaries)]
        (export/ensure-not-cancelled!)
        (let [preview-id (str (:id summary))
              preview (or (api-v2/try-get-json (:base-url ctx)
                                               (api-v2/preview-path preview-id)
                                               (:http-opts ctx)
                                               nil)
                          summary)
              file-name (let [filename (:filename preview)]
                          (if (clojure.string/blank? (str filename))
                            preview-id
                            filename))
              preview-path (str previews-dir File/separator file-name)
              response (api-v2/get-stream! (:base-url ctx)
                                           (api-v2/preview-stream-path preview-id)
                                           (:http-opts ctx))
              overall (+ 0.80 (* 0.15 (/ (inc idx) preview-n)))]
          (files/save-http-stream! response preview-path nil)
          (progress/set-item-progress! item-id overall
                                       {:progress-label "preview"
                                        :file-progress 1.0}))))))

(defn- download-media-file [ctx target-dir item-id media-entry-id]
  (export/ensure-not-cancelled!)
  (if-let [media-file (api-v2/try-get-json (:base-url ctx)
                                           (api-v2/media-file-path media-entry-id)
                                           (:http-opts ctx)
                                           nil)]
    (let [item-id (str item-id)
          media-file-id (str (:id media-file))
          media-files-dir (str target-dir File/separator "media-files")
          media-file-dir (str media-files-dir File/separator media-file-id)
          data-path (str media-file-dir File/separator "data.json")
          file-name (let [filename (:filename media-file)]
                      (if (clojure.string/blank? (str filename))
                        media-file-id
                        filename))
          file-path (str media-file-dir File/separator file-name)
          response (api-v2/get-stream! (:base-url ctx)
                                       (api-v2/media-file-stream-path media-file-id)
                                       (:http-opts ctx))]
      (io/make-parents data-path)
      (spit data-path (cheshire/generate-string media-file {:pretty true}))
      (progress/set-item-progress! item-id 0.15
                                   {:progress-label "file"
                                    :file-progress 0.0})
      (files/save-http-stream!
       response file-path
       (fn [read total]
         (when (and total (pos? total))
           (let [frac (/ (double read) total)]
             (progress/set-item-progress!
              item-id (files/file-overall-progress frac)
              {:progress-label "file"
               :file-progress (double frac)})))))
      (progress/set-item-progress! item-id 0.80
                                   {:progress-label "file"
                                    :file-progress 1.0})
      (let [summaries (or (seq (:previews media-file))
                          (api-v2/try-get-json (:base-url ctx)
                                               (api-v2/previews-path media-entry-id)
                                               (:http-opts ctx)
                                               nil))]
        (download-previews ctx media-file-dir item-id media-entry-id media-file-id
                           (or summaries []))))
    (warn "api-v2 media entry has no media-file" {:id media-entry-id})))

(defn download-media-entry
  [ctx id target-dir skip-media-files? prefix-meta-key]
  (catcher/with-logging {}
    (export/ensure-not-cancelled!)
    (let [raw-id (str id)
          entity (api-v2/get-json! (:base-url ctx)
                                   (api-v2/media-entry-path raw-id)
                                   (:http-opts ctx)
                                   nil)
          entity-md (load-meta-data ctx :media-entry raw-id)
          entry-prefix (folder-name prefix-meta-key entity-md raw-id)
          entry-dir-path (str target-dir File/separator entry-prefix)
          item-id (api/scoped-item-id raw-id)
          item-title (meta-data/title entity-md)
          claim (export/claim-item!
                 item-id
                 (assoc entity
                        :id raw-id
                        :state "downloading"
                        :errors {}
                        :type "MediaEntry"
                        :title item-title
                        :path entry-dir-path
                        :api-label api/*export-api-label*
                        :progress 0.05
                        :progress-label "meta"
                        :file-progress 0.0
                        :download_started-at (str (clj-time.core/now))))]
      (case (:action claim)
        :symlink (export/symlink item-id entry-dir-path (:target claim))
        :download
        (do (export/ensure-not-cancelled!)
            (export/delete-path-if-exists! entry-dir-path)
            (io/make-parents entry-dir-path)
            (meta-data/write-meta-data entry-dir-path entity-md item-id
                                       entry-prefix)
            (index-html/write entry-dir-path
                              entity-md
                              {:id raw-id :type :media-entry}
                              entry-prefix)
            (progress/set-item-progress! item-id 0.15 {:progress-label "meta"})
            (when-not skip-media-files?
              (download-media-file ctx entry-dir-path item-id raw-id))
            (export/set-item-to-finished item-id))))))

(declare download-set)

(defn- download-child-entries [ctx collection-id target-dir skip-media-files? prefix-meta-key]
  (let [ids (api-v2/child-ids
             (:base-url ctx) (:http-opts ctx)
             "/media-entries/"
             (api-v2/child-media-entries-query
              collection-id (api-v2/authenticated? (:http-opts ctx)))
             [:data :media_entries])]
    (export/run-bounded!
     4
     (fn [entry-id]
       (export/ensure-not-cancelled!)
       (download-media-entry ctx entry-id target-dir skip-media-files? prefix-meta-key))
     ids)))

(defn- download-child-collections [ctx collection-id target-dir recursive? skip-media-files? prefix-meta-key]
  (let [ids (api-v2/child-ids
             (:base-url ctx) (:http-opts ctx)
             "/collections/"
             (api-v2/child-collections-query
              collection-id (api-v2/authenticated? (:http-opts ctx)))
             [:data :collections])]
    (doseq [child-id ids]
      (export/ensure-not-cancelled!)
      (download-set ctx child-id target-dir recursive? skip-media-files? prefix-meta-key))))

(defn download-set
  [ctx id target-dir recursive? skip-media-files? prefix-meta-key]
  (export/ensure-not-cancelled!)
  (let [raw-id (str id)
        entity (api-v2/get-json! (:base-url ctx)
                                 (api-v2/collection-path raw-id)
                                 (:http-opts ctx)
                                 nil)
        entity-md (load-meta-data ctx :collection raw-id)
        path-prefix (folder-name prefix-meta-key entity-md raw-id)
        target-dir-path (str target-dir File/separator path-prefix)
        item-id (api/scoped-item-id raw-id)
        item-title (meta-data/title entity-md)
        claim (export/claim-item!
               item-id
               (assoc entity
                      :id raw-id
                      :state "downloading"
                      :errors {}
                      :type "Collection"
                      :title item-title
                      :path target-dir-path
                      :api-label api/*export-api-label*
                      :progress 0.1
                      :progress-label "meta"
                      :file-progress 0.0
                      :download_started-at (str (clj-time.core/now))))]
    (case (:action claim)
      :symlink (export/symlink item-id target-dir-path (:target claim))
      :download
      (catcher/with-logging {}
        (export/ensure-not-cancelled!)
        (export/delete-path-if-exists! target-dir-path)
        (io/make-parents target-dir-path)
        (meta-data/write-meta-data target-dir-path entity-md item-id path-prefix)
        (index-html/write target-dir-path
                          entity-md
                          {:id raw-id :type :collection}
                          path-prefix)
        (progress/set-item-progress! item-id 0.3 {:progress-label "meta"})
        (download-child-entries ctx raw-id target-dir-path skip-media-files? prefix-meta-key)
        (when recursive?
          (download-child-collections ctx raw-id target-dir-path recursive?
                                      skip-media-files? prefix-meta-key))
        (export/set-item-to-finished item-id)))))

(defn download-meta-data-schema
  [base-url http-opts target-dir]
  (let [path (str target-dir File/separator "meta-data_schema.json")
        keys (api-v2/meta-keys base-url http-opts)
        by-vocab (group-by #(str (:vocabulary_id %)) keys)
        schema {:vocabularies
                (->> (api-v2/vocabularies base-url http-opts)
                     (map (fn [vocab]
                            (assoc vocab
                                   :meta-keys
                                   (->> (get by-vocab (str (:id vocab)) [])
                                        (sort-by #(or (:position %) 0))
                                        vec))))
                     vec)}]
    (io/make-parents path)
    (spit path (cheshire/generate-string schema {:pretty true}))))
