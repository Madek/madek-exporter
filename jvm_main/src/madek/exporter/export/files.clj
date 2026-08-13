(ns madek.exporter.export.files
  (:require
   [cheshire.core :as cheshire]
   [clojure.java.io :as io]
   [clojure.tools.logging :as logging]
   [json-roa.client.core :as roa]
   [logbug.catcher :as catcher]
   [logbug.debug :as debug :refer [identity-with-logging I> I>>]]
   [logbug.thrown :as thrown]
   [madek.exporter.export.progress :as progress]
   [madek.exporter.state :as state]
   [madek.exporter.utils :refer [deep-merge presence]])

  (:import
   [java.io File InputStream OutputStream]))

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
  "Legacy filename sanitizer; prefer madek.exporter.export.structure/legacy-usable-filename."
  (.replaceAll (str s) "[^a-zA-Z0-9 ]" ""))

(defn path-prefix [prefix-meta-key media-resource]
  "Legacy dir naming ({title}_{uuid}); prefer structure/dir-name with a strategy."
  (let [prefix-part-one (if-not (presence prefix-meta-key) ""
                                (if-let [mk-value (get-prefix
                                                   prefix-meta-key media-resource)]
                                  (str (useableFileName mk-value) "_") ""))]
    (str prefix-part-one (-> media-resource roa/data :id))))

;### Progress-aware copy ######################################################

(defn- ensure-not-cancelled!
  []
  (when (get-in @state/db [:download :cancel-requested])
    (throw (ex-info "Download cancelled by user"
                    {:type :download-cancelled}))))

(defn- header-content-length [response]
  (let [h (or (:headers response) {})
        raw (or (get h "Content-Length")
                (get h "content-length")
                (get h "Content-length"))]
    (when raw
      (try
        (Long/parseLong (str raw))
        (catch Exception _ nil)))))

(defn- copy-counting!
  "Copy in to out. When content-length is known, call on-progress with
  [bytes-read total] at most every 5% or 250ms. Checks cancel each buffer read."
  [^InputStream in ^OutputStream out content-length on-progress]
  (let [buf (byte-array 8192)
        last-report (atom {:t 0 :pct -1})
        report! (fn [n]
                  (when (and on-progress content-length (pos? content-length))
                    (let [now (System/currentTimeMillis)
                          file-pct (int (* 100 (/ (double n) content-length)))
                          prev @last-report]
                      (when (or (>= (- now (:t prev)) 250)
                                (>= (- file-pct (:pct prev)) 5)
                                (>= n content-length))
                        (reset! last-report {:t now :pct file-pct})
                        (on-progress n content-length)))))]
    (loop [n 0]
      (ensure-not-cancelled!)
      (let [r (.read in buf)]
        (if (neg? r)
          (do
            (when on-progress
              (on-progress n content-length))
            n)
          (do
            (.write out buf 0 r)
            (let [n' (+ n r)]
              (report! n')
              (recur n'))))))))

(defn- file-overall-progress
  "Map file byte fraction into MediaEntry overall progress 0.15 to 0.80."
  [file-frac]
  (+ 0.15 (* 0.65 (max 0.0 (min 1.0 file-frac)))))

;### DL Previews ##############################################################

(defn download-previews [target-dir media-file item-id media-file-id]
  (let [item-id (str item-id)
        previews-dir (str target-dir File/separator "previews")
        preview-rels (doall (-> media-file roa/coll-seq))
        preview-n (count preview-rels)]
    (if (zero? preview-n)
      (progress/set-item-progress! item-id 0.95
                                   {:progress-label "preview"
                                    :file-progress 1.0})
      (doseq [[idx preview-rel] (map-indexed vector preview-rels)]
        (ensure-not-cancelled!)
        (let [preview (roa/get preview-rel {})
              preview-id (-> preview roa/data :id)
              preview-path (str previews-dir File/separator
                                (-> preview roa/data :filename))
              preview-response (-> preview
                                   (roa/relation :data-stream)
                                   (roa/get {} :mod-conn-opts #(assoc % :as :stream)))
              overall (+ 0.80 (* 0.15 (/ (inc idx) preview-n)))]
          (swap! state/db
                 (fn [db params]
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
          (with-open [out (io/output-stream (io/file preview-path))]
            (io/copy (-> preview-response :body) out))
          (progress/set-item-progress! item-id overall
                                       {:progress-label "preview"
                                        :file-progress 1.0}))))))

;### DL Media-Files ###########################################################

(defn download-media-file [target-dir media-file item-id]
  (ensure-not-cancelled!)
  (let [item-id (str item-id)
        media-file-id (-> media-file roa/data :id)
        response (-> media-file
                     (roa/relation :data-stream)
                     (roa/get {} :mod-conn-opts #(assoc % :as :stream)))
        content-length (header-content-length response)
        file-name (let [filename (-> media-file roa/data :filename)]
                    (if (clojure.string/blank? filename)
                      media-file-id
                      filename))
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
    (progress/set-item-progress! item-id 0.15
                                 {:progress-label "file"
                                  :file-progress 0.0})
    (io/make-parents file-path)
    (with-open [out (io/output-stream (io/file file-path))]
      (copy-counting!
       (:body response) out content-length
       (when content-length
         (fn [read total]
           (let [frac (/ (double read) total)]
             (progress/set-item-progress!
              item-id (file-overall-progress frac)
              {:progress-label "file"
               :file-progress (double frac)}))))))
    (progress/set-item-progress! item-id 0.80
                                 {:progress-label "file"
                                  :file-progress 1.0})
    (download-previews target-dir media-file item-id media-file-id)))

(defn download-media-files [target-dir media-entry]
  (catcher/with-logging {}
    (let [item-id (str (-> media-entry roa/data :id))
          media-files-dir (str target-dir File/separator "media-files")]
      (doseq [media-file [(-> media-entry (roa/relation :media-file) (roa/get {}))]]
        (let [media-file-data (roa/data media-file)
              media-file-dir (str media-files-dir File/separator (:id media-file-data))
              media-file-data-path (str media-file-dir File/separator "data.json")]
          (io/make-parents media-file-data-path)
          (spit media-file-data-path
                (cheshire/generate-string media-file-data {:pretty true}))
          (download-media-file media-file-dir media-file item-id))))))

;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'ring.middleware.resource)
;(debug/debug-ns *ns*)
;(debug/debug-ns 'json-roa.client.core)
;(debug/debug-ns 'uritemplate-clj.core)
