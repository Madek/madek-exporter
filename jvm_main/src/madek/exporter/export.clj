(ns madek.exporter.export
  (:require
   [cheshire.core :as cheshire]
   [clj-time.core :as time]
   [clojure.java.io :as io]
   [json-roa.client.core :as roa]
   [logbug.catcher :as catcher :refer [snatch]]
   [logbug.debug :as debug :refer [identity-with-logging I> I>>]]
   [logbug.thrown :as thrown]
   [madek.exporter.export.control :as control]
   [madek.exporter.export.files :as files :refer [download-media-files]]
   [madek.exporter.export.index-html :as index-html]
   [madek.exporter.export.meta-data :as meta-data]
   [madek.exporter.export.meta-data-schema :as meta-data-schema]
   [madek.exporter.export.progress :as progress]
   [madek.exporter.export.structure :as structure]
   [madek.exporter.state :as state]
   [madek.exporter.utils :refer [authenticated-http-options? deep-merge presence]]
   [taoensso.timbre :as logging :refer [debug]])

  (:import
   [java.io File]
   [java.nio.file Files Paths]
   [java.util.concurrent Callable Executors Future TimeUnit]
   [org.apache.commons.io FileUtils]))

(def ^:private media-entry-concurrency 4)

(def ^:private pool-await-seconds 5)

(defonce active-download-pool (atom nil))

;### Cancel ###################################################################

(def cancel-requested? control/cancel-requested?)
(def ensure-not-cancelled! control/ensure-not-cancelled!)

(defn abort-parallel-downloads!
  "Shut down the active media-entry worker pool immediately (cancel path)
  and wait briefly for workers to exit."
  []
  (loop []
    (when-let [^java.util.concurrent.ExecutorService pool
               @active-download-pool]
      (if (compare-and-set! active-download-pool pool nil)
        (do (.shutdownNow pool)
            (.awaitTermination pool pool-await-seconds TimeUnit/SECONDS))
        (recur)))))
;### Path Helper ##############################################################

(defn nio-path [s] (Paths/get s (into-array [""])))

(defn delete-path-if-exists!
  "Remove an existing export path so a re-download starts clean."
  [path]
  (when (presence (some-> path str))
    (let [f (io/file path)]
      (when (.exists f)
        (if (.isDirectory f)
          (FileUtils/deleteDirectory f)
          (io/delete-file f true))))))

(defn symlink [id source target]
  (snatch
   {:level :debug
    :throwable java.nio.file.FileAlreadyExistsException}
   (let [id (str id)]
     (swap! state/db
            (fn [db id source target]
              (deep-merge db {:download
                              {:items
                               {id
                                {:links
                                 {source target}}}}}))
            id source target))
   (Files/createSymbolicLink
    (nio-path source)
    (nio-path target)
    (make-array java.nio.file.attribute.FileAttribute 0))))

;### Atomic claim #############################################################

(defn claim-item!
  "Atomically register an item under (str id). Returns
   {:action :download} when this caller owns the download, or
   {:action :symlink :target path} when the item was already claimed."
  [id claimed-data]
  (ensure-not-cancelled!)
  (let [id (str id)
        outcome (atom nil)]
    (swap! state/db
           (fn [db]
             (if-let [existing (get-in db [:download :items id])]
               (do (reset! outcome {:action :symlink :target (:path existing)})
                   db)
               (do (reset! outcome {:action :download})
                   (assoc-in db [:download :items id]
                             (assoc claimed-data :id id))))))
    @outcome))

;### DL Media-Entry ###########################################################

(defn set-item-to-finished [id]
  (ensure-not-cancelled!)
  (let [id (str id)]
    (progress/set-item-progress! id 1.0 {:progress-label "done"
                                         :file-progress 1.0})
    (swap! state/db
           (fn [db id]
             (deep-merge db
                         {:download
                          {:items
                           {id
                            {:state "passed"
                             :download_finished-at (str (time/now))}}}}))
           id)))

(defn download-media-entry
  ([id target-dir skip-media-files? prefix-meta-key api-entry-point api-http-opts]
   (download-media-entry id target-dir skip-media-files? prefix-meta-key
                         structure/new-export-structure
                         api-entry-point api-http-opts))
  ([id target-dir skip-media-files? prefix-meta-key export-structure
    api-entry-point api-http-opts]
   (catcher/with-logging {}
     (let [media-entry (I> identity-with-logging
                           (roa/get-root api-entry-point
                                         :default-conn-opts api-http-opts)
                           (roa/relation :media-entry)
                           (roa/get {:id id}))]
       (download-media-entry skip-media-files? prefix-meta-key export-structure
                             target-dir media-entry))))
  ([skip-media-files? prefix-meta-key export-structure dir-path media-entry]
   (catcher/with-logging {}
     (ensure-not-cancelled!)
     (let [id (str (-> media-entry roa/data :id))
           entry-prefix-path (structure/dir-name export-structure
                                                 prefix-meta-key media-entry)
           entry-dir-path (str dir-path File/separator entry-prefix-path)
           write-prefixed? (structure/write-prefixed-artifacts? export-structure)
           entity-md (meta-data/meta-data media-entry)
           item-title (meta-data/title entity-md)
           claim (claim-item!
                  id
                  (assoc (roa/data media-entry)
                         :state "downloading"
                         :errors {}
                         :type "MediaEntry"
                         :title item-title
                         :path entry-dir-path
                         :progress 0.05
                         :progress-label "meta"
                         :file-progress 0.0
                         :download_started-at (str (time/now))))]
       (case (:action claim)
         :symlink (symlink id entry-dir-path (:target claim))
         :download
         (do (ensure-not-cancelled!)
             (delete-path-if-exists! entry-dir-path)
             (io/make-parents entry-dir-path)
             (meta-data/write-meta-data entry-dir-path entity-md id
                                        entry-prefix-path write-prefixed?)
             (index-html/write entry-dir-path
                               entity-md
                               (-> media-entry roa/data (assoc :type :media-entry))
                               entry-prefix-path
                               write-prefixed?)
             (progress/set-item-progress! id 0.15 {:progress-label "meta"})
             (when-not skip-media-files?
               (download-media-files entry-dir-path media-entry))
             (set-item-to-finished id)))))))

;### Parallel helpers #########################################################

(defn- unwrap-execution-exception [e]
  (if (instance? java.util.concurrent.ExecutionException e)
    (or (.getCause e) e)
    e))

(defn run-bounded!
  "Run (f item) for each item with at most n worker threads. Rethrows the first
  worker failure and cancels remaining tasks. Re-binds download generation on
  each worker so cancel/supersede checks work off the pool threads."
  [n f items]
  (when (seq items)
    (let [pool (Executors/newFixedThreadPool (int n))
          futs (atom [])
          gen control/*download-generation*]
      (reset! active-download-pool pool)
      (try
        (doseq [item items]
          (ensure-not-cancelled!)
          (swap! futs conj
                 (.submit pool
                          ^Callable
                          (reify Callable
                            (call [_]
                              (binding [control/*download-generation* gen]
                                (f item)))))))
        (doseq [^Future fut @futs]
          (try
            (.get fut)
            (catch Exception e
              (doseq [^Future other @futs]
                (.cancel other true))
              (throw (unwrap-execution-exception e)))))
        (finally
          (compare-and-set! active-download-pool pool nil)
          (.shutdownNow pool)
          (.awaitTermination pool pool-await-seconds TimeUnit/SECONDS))))))
;### check credentials ########################################################

(defn check-credentials [api-entry-point api-http-opts]
  (let [response (-> (roa/get-root api-entry-point :default-conn-opts api-http-opts)
                     (roa/relation :auth-info)
                     (roa/get {}))]
    (debug (-> response roa/data))))

;### DL Set ###################################################################

(declare download-set)

(defn download-media-entries-for-set [id target-dir-path skip-media-files? prefix-meta-key
                                      export-structure api-entry-point api-http-opts]
  (let [me-get-opts (merge {:collection_id id}
                           (if (authenticated-http-options? api-http-opts)
                             {:me_get_full_size "true"}
                             {:public_get_full_size "true"}))
        me-rels (doall
                 (I> identity-with-logging
                     (roa/get-root api-entry-point
                                   :default-conn-opts api-http-opts)
                     (roa/relation :media-entries)
                     (roa/get me-get-opts)
                     roa/coll-seq))]
    (run-bounded!
     media-entry-concurrency
     (fn [me-rel]
       (ensure-not-cancelled!)
       (download-media-entry skip-media-files? prefix-meta-key export-structure
                             target-dir-path (roa/get me-rel {})))
     me-rels)))

(defn download-collections-for-collection [collection target-dir-path recursive? skip-media-files?
                                           prefix-meta-key export-structure
                                           api-entry-point api-http-opts]
  (let [coll-get-opts (if (authenticated-http-options? api-http-opts)
                        {:me_get_metadata_and_previews "true"}
                        {:public_get_metadata_and_previews "true"})]
    (doseq [collection (I>> identity-with-logging
                            (I> identity-with-logging
                                collection
                                (roa/relation :collections)
                                (roa/get coll-get-opts)
                                roa/coll-seq)
                            (map #(roa/get % {})))]
      (ensure-not-cancelled!)
      (download-set
       (-> collection roa/data :id)
       target-dir-path recursive? skip-media-files? prefix-meta-key
       export-structure api-entry-point api-http-opts))))

(defn download-set [id dl-path recursive? skip-media-files? prefix-meta-key
                    export-structure api-entry-point api-http-opts]
  (ensure-not-cancelled!)
  (let [id (str id)
        collection (-> (roa/get-root api-entry-point
                                     :default-conn-opts api-http-opts)
                       (roa/relation :collection)
                       (roa/get {:id id}))
        path-prefix (structure/dir-name export-structure prefix-meta-key collection)
        target-dir-path (str dl-path File/separator path-prefix)
        write-prefixed? (structure/write-prefixed-artifacts? export-structure)
        entity-md (meta-data/meta-data collection)
        item-title (meta-data/title entity-md)
        claim (claim-item!
               id
               (assoc (roa/data collection)
                      :state "downloading"
                      :errors {}
                      :type "Collection"
                      :title item-title
                      :path target-dir-path
                      :progress 0.1
                      :progress-label "meta"
                      :file-progress 0.0
                      :download_started-at (str (time/now))))]
    (case (:action claim)
      :symlink (symlink id target-dir-path (:target claim))
      :download
      (catcher/with-logging {}
        (ensure-not-cancelled!)
        (delete-path-if-exists! target-dir-path)
        (io/make-parents target-dir-path)
        (meta-data/write-meta-data target-dir-path entity-md id
                                   path-prefix write-prefixed?)
        (index-html/write target-dir-path
                          entity-md
                          (-> collection roa/data (assoc :type :collection))
                          path-prefix
                          write-prefixed?)
        (progress/set-item-progress! id 0.3 {:progress-label "meta"})
        (download-media-entries-for-set
         id target-dir-path skip-media-files? prefix-meta-key
         export-structure api-entry-point api-http-opts)
        (when recursive?
          (download-collections-for-collection
           collection target-dir-path recursive? skip-media-files? prefix-meta-key
           export-structure api-entry-point api-http-opts))
        (set-item-to-finished id)))))

;### download meta-data schema ################################################

(def download-meta-data-schema meta-data-schema/download)

;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'ring.middleware.resource)
(debug/debug-ns *ns*)
;(debug/debug-ns 'json-roa.client.core)
;(debug/debug-ns 'uritemplate-clj.core)
