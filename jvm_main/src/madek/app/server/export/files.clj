(ns madek.app.server.export.files
  (:require
    [madek.app.server.state :as state]
    [madek.app.server.utils :refer [deep-merge presence]]

    [json-roa.client.core :as roa]
    [cheshire.core :as cheshire]
    [clojure.java.io :as io]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [identity-with-logging I> I>>]]
    [logbug.thrown :as thrown]
    )

  (:import
    [java.io File]
    ))

;### title and prefix #########################################################

(defn get-prefix [prefix-meta-key media-resource]
  (-> media-resource
      (roa/relation :meta-data)
      (roa/get {:meta_keys (cheshire/generate-string [(str prefix-meta-key)])})
      roa/coll-seq
      first
      (roa/get {})
      roa/data
      :value str))

(defn useableFileName [s]
  (.replaceAll s "[^a-zA-Z0-9 ]" ""))

(defn path-prefix [prefix-meta-key media-resource]
  (let [prefix-part-one (if-not (presence prefix-meta-key) ""
                          (if-let [mk-value (get-prefix
                                              prefix-meta-key media-resource)]
                            (str (useableFileName mk-value) "_") ""))]
    (str prefix-part-one (-> media-resource roa/data :id))))



;### DL Previews ##############################################################

(defn download-previews [target-dir media-file item-id media-file-id]
  (let [previews-dir (str target-dir File/separator "previews")]
    (doseq [preview-rel (-> media-file roa/coll-seq)]
      (let [preview (roa/get preview-rel {})
            preview-id  (-> preview roa/data :id)
            preview-path (str previews-dir File/separator
                              (-> preview roa/data :filename))
            preview-response (-> preview
                                 (roa/relation :data-stream)
                                 (roa/get {} :mod-conn-opts #(assoc % :as :stream)))]
        (swap! state/db
               (fn [db  params]
                 (deep-merge db params))
               {:download
                {:items
                 {item-id
                  {:media-files
                   {media-file-id
                    {:previews
                     {preview-id
                      {:path preview-path}}}}}}}})
        (io/make-parents preview-path)
        (clojure.java.io/copy (-> preview-response :body)
                              (clojure.java.io/file preview-path))
        ))))

;### DL Media-Files ###########################################################

(defn download-media-file [target-dir media-file item-id]
  (let [media-file-id (-> media-file roa/data :id)
        response (-> media-file
                     (roa/relation :data-stream)
                     (roa/get {} :mod-conn-opts #(assoc % :as :stream)))
        file-name (let [filename (-> media-file roa/data :filename)]
                    (if (clojure.string/blank? filename)
                      media-file-id filename))
        file-path (str target-dir File/separator file-name)]
    (swap! state/db
           (fn [db params]
             (deep-merge db params))
           {:download
            {:items
             {item-id
              {:media-files
               {media-file-id
                {:path file-path}}}}}})
    (io/make-parents file-path)
    (clojure.java.io/copy (-> response :body) (clojure.java.io/file file-path))
    (download-previews target-dir media-file item-id media-file-id)
    ))

(defn download-media-files [target-dir media-entry]
  (catcher/with-logging {}
    (let [item-id (-> media-entry roa/data :id)
          media-files-dir (str target-dir File/separator "media-files")]
      (doseq [media-file [(-> media-entry (roa/relation :media-file) (roa/get {}))]]
        (let [media-file-data (roa/data media-file)
              media-file-dir (str media-files-dir File/separator (:id media-file-data))
              media-file-data-path (str media-file-dir File/separator "data.json")]
          (io/make-parents media-file-data-path)
          (spit media-file-data-path (cheshire/generate-string media-file-data {:pretty true}))
          (download-media-file media-file-dir media-file item-id)
          )))))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'ring.middleware.resource)
;(debug/debug-ns *ns*)
;(debug/debug-ns 'json-roa.client.core)
;(debug/debug-ns 'uritemplate-clj.core)
