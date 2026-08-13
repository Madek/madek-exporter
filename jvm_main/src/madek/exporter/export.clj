(ns madek.exporter.export
  (:require
   [cheshire.core :as cheshire]
   [clj-time.core :as time]
   [clojure.java.io :as io]
   [json-roa.client.core :as roa]
   [logbug.catcher :as catcher :refer [snatch]]
   [logbug.debug :as debug :refer [identity-with-logging I> I>>]]
   [logbug.thrown :as thrown]
   [madek.exporter.export.files :as files :refer [download-media-files]]
   [madek.exporter.export.index-html :as index-html]
   [madek.exporter.export.meta-data :as meta-data]
   [madek.exporter.export.meta-data-schema :as meta-data-schema]
   [madek.exporter.export.structure :as structure]
   [madek.exporter.state :as state]
   [madek.exporter.utils :refer [authenticated-http-options? deep-merge presence]]
   [taoensso.timbre :as logging :refer [debug]])

  (:import
   [java.io File]
   [java.nio.file Files Paths]))

;### Cancel ###################################################################

(defn cancel-requested? []
  (boolean (get-in @state/db [:download :cancel-requested])))

(defn ensure-not-cancelled! []
  (when (cancel-requested?)
    (throw (ex-info "Download cancelled by user"
                    {:type :download-cancelled}))))

;### Path Helper ##############################################################

(defn nio-path [s] (Paths/get s (into-array [""])))

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

;### DL Media-Entry ###########################################################

(defn set-item-to-finished [id]
  (let [id (str id)]
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
           item-title (meta-data/title entity-md)]
       (if (-> @state/db :download :items (get id))
         (let [target (-> @state/db :download :items (get id) :path)]
           (symlink id entry-dir-path target))
         (do (swap! state/db (fn [db uuid media-entry]
                               (assoc-in db [:download :items id] media-entry))
                    id (assoc (roa/data media-entry)
                              :state "downloading"
                              :errors {}
                              :type "MediaEntry"
                              :title item-title
                              :path entry-dir-path
                              :download_started-at (str (time/now))))
             (io/make-parents entry-dir-path)
             (meta-data/write-meta-data entry-dir-path entity-md id
                                        entry-prefix-path write-prefixed?)
             (index-html/write entry-dir-path
                               entity-md
                               (-> media-entry roa/data (assoc :type :media-entry))
                               entry-prefix-path
                               write-prefixed?)
             (when-not skip-media-files?
               (download-media-files entry-dir-path media-entry))
             (set-item-to-finished id)))))))

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
                             {:public_get_full_size "true"}))]
    (doseq [me-rel (I> identity-with-logging
                       (roa/get-root api-entry-point
                                     :default-conn-opts api-http-opts)
                       (roa/relation :media-entries)
                       (roa/get me-get-opts)
                       roa/coll-seq)]
      (ensure-not-cancelled!)
      (download-media-entry skip-media-files? prefix-meta-key export-structure
                            target-dir-path (roa/get me-rel {})))))

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
        item-title (meta-data/title entity-md)]
    (if (-> @state/db :download :items (get id))
      (let [target (-> @state/db :download :items (get id) :path)]
        (symlink id target-dir-path target))
      (catcher/with-logging {}
        (swap! state/db (fn [db id] (deep-merge db {:download {:items {id {}}}})) id)
        (swap! state/db (fn [db uuid collection]
                          (assoc-in db [:download :items id] collection))
               id (assoc (roa/data collection)
                         :state "downloading"
                         :errors {}
                         :type "Collection"
                         :title item-title
                         :path target-dir-path
                         :download_started-at (str (time/now))))
        (io/make-parents target-dir-path)
        (meta-data/write-meta-data target-dir-path entity-md id
                                   path-prefix write-prefixed?)
        (index-html/write target-dir-path
                          entity-md
                          (-> collection roa/data (assoc :type :collection))
                          path-prefix
                          write-prefixed?)
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
