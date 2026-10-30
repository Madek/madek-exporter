(ns madek.exporter.export-fake-api-test
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is use-fixtures]]
   [madek.exporter.connection :as connection]
   [madek.exporter.export :as export]
   [madek.exporter.export.meta-data-schema :as export-meta-data-schema]
   [madek.exporter.export.meta-keys :as export-meta-keys]
   [madek.exporter.request-log :as request-log]
   [madek.exporter.server.web :as web]
   [madek.exporter.state :as state]
   ))

(def ^:private fake-base-url (System/getenv "FAKE_MADEK_API_TEST_URL"))
(def ^:private valid-token "test-token")
(def ^:private media-entry-id "00000000-0000-4000-8000-000000000010")
(def ^:private set-id "00000000-0000-4000-8000-000000000020")
(def ^:private set-entry-ids
  ["00000000-0000-4000-8000-000000000010"
   "00000000-0000-4000-8000-000000000021"
   "00000000-0000-4000-8000-000000000022"])

;; Keep in sync with fakeExportLogCounts in tests/e2e/electron-app.js
(def ^:private expected-log-counts
  {:media-entry-metadata 12
   :media-entry-with-file 14
   :set-metadata 23
   :media-entry-both-apis 17})

(defn- require-fake! []
  (when-not fake-base-url
    (throw (ex-info "Set FAKE_MADEK_API_TEST_URL (run ./bin/test-login)" {}))))

(defn clear-state-fixture [f]
  (export-meta-data-schema/reset-cache!)
  (export-meta-keys/reset-cache!)
  (swap! state/db assoc :connection {} :download {:target-directory
                                                  (java.io.File.
                                                   (System/getProperty "java.io.tmpdir")
                                                   "madek-export-test")})
  (f))

(use-fixtures :each clear-state-fixture)

(defn- connect-fake!
  [& {:keys [api-mode] :or {api-mode :api}}]
  (require-fake!)
  (let [response (connection/connect-to-madek-server
                  {:body {:url fake-base-url
                          :api-token valid-token
                          :sign-in-method :token
                          :api-mode (name api-mode)}})]
    (is (= 202 (:status response))
        "connect to fake Madek API")))

(defn- web-export-wait!
  "POST /download flow; returns HTTP log lines when the export future completes."
  [entity-id entity-type url-suffix target-dir skip-media-files?
   & {:keys [api-mode] :or {api-mode :api}}]
  (let [target-dir (doto target-dir (.mkdirs) (.deleteOnExit))]
    (connect-fake! :api-mode api-mode)
    (is (= 204 (:status (web/download-step1
                         {:body {:entity-id entity-id
                                 :entity-type entity-type
                                 :url (str fake-base-url url-suffix)
                                 :target-directory (.getAbsolutePath target-dir)}}))))
    (is (= 204 (:status (web/patch-download
                         {:body {:step2-completed true
                                 :skip_media_files skip-media-files?
                                 :recursive false}}))))
    (is (= 202 (:status (web/download {}))))
    (loop [n 0]
      (cond
        (get-in @state/db [:download :download-finished]) (request-log/lines)
        (> n 200) (do (is false "export did not finish in time") [])
        :else (do (Thread/sleep 100) (recur (inc n)))))))

(deftest export-fake-media-entry-skip-media-files
  (require-fake!)
  (let [target-dir (doto (io/file (System/getProperty "java.io.tmpdir")
                                  (str "madek-export-" (System/nanoTime)))
                   (.mkdirs)
                   (.deleteOnExit))
        response (connection/connect-to-madek-server
                  {:body {:url fake-base-url
                          :api-token valid-token
                          :sign-in-method :token}})]
    (is (= 202 (:status response)))
    (export/download-media-entry media-entry-id
                                 (.getAbsolutePath target-dir)
                                 true
                                 nil
                                 (state/connection-entry-point)
                                 (state/connection-http-options))
    (let [entry-dir (io/file target-dir media-entry-id)
          meta-data (io/file entry-dir "meta-data.json")]
      (is (.exists meta-data) "writes meta-data.json")
      (is (re-find #"madek_core:title" (slurp meta-data))))))

(deftest export-fake-set-three-media-entries-skip-files
  (require-fake!)
  (let [target-dir (doto (io/file (System/getProperty "java.io.tmpdir")
                                  (str "madek-set-export-" (System/nanoTime)))
                   (.mkdirs)
                   (.deleteOnExit))
        response (connection/connect-to-madek-server
                  {:body {:url fake-base-url
                          :api-token valid-token
                          :sign-in-method :token}})]
    (is (= 202 (:status response)))
    (export/download-set set-id
                         (.getAbsolutePath target-dir)
                         false
                         true
                         nil
                         (state/connection-entry-point)
                         (state/connection-http-options))
    (let [set-dir (io/file target-dir set-id)]
      (is (.exists (io/file set-dir "meta-data.json")))
      (doseq [entry-id set-entry-ids]
        (is (.exists (io/file set-dir (str entry-id) "meta-data.json"))
            (str "entry " entry-id " meta-data"))))))

(deftest web-download-media-entry-both-apis-log-count
  (let [target-dir (io/file (System/getProperty "java.io.tmpdir")
                            (str "madek-web-entry-both-" (System/nanoTime)))
        logs (web-export-wait! media-entry-id "entry"
                               (str "/entries/" media-entry-id)
                               target-dir true
                               :api-mode :both)
        api-tree (io/file target-dir "api" media-entry-id)
        v2-tree (io/file target-dir "api-v2" media-entry-id)]
    (is (= (:media-entry-both-apis expected-log-counts) (count logs))
        (str "both-apis log count (got " (count logs) ")"))
    (is (.exists (io/file api-tree "meta-data.json")))
    (is (.exists (io/file v2-tree "meta-data.json")))))

(deftest web-download-media-entry-metadata-log-count
  (let [target-dir (io/file (System/getProperty "java.io.tmpdir")
                            (str "madek-web-entry-meta-" (System/nanoTime)))
        logs (web-export-wait! media-entry-id "entry"
                               (str "/entries/" media-entry-id)
                               target-dir true)]
    (is (= (:media-entry-metadata expected-log-counts) (count logs)))))

(deftest web-download-media-entry-with-file-log-count
  (let [target-dir (io/file (System/getProperty "java.io.tmpdir")
                            (str "madek-web-entry-file-" (System/nanoTime)))
        logs (web-export-wait! media-entry-id "entry"
                               (str "/entries/" media-entry-id)
                               target-dir false)]
    (is (= (:media-entry-with-file expected-log-counts) (count logs)))))

(deftest web-download-set-end-to-end-skip-files
  (let [target-dir (io/file (System/getProperty "java.io.tmpdir")
                            (str "madek-web-set-" (System/nanoTime)))
        logs (web-export-wait! set-id "set"
                               (str "/sets/" set-id)
                               target-dir true)
        set-dir (io/file target-dir set-id)]
    (is (= (:set-metadata expected-log-counts) (count logs)))
    (is (.exists (io/file set-dir "meta-data.json")))
    (doseq [entry-id set-entry-ids]
      (is (.exists (io/file set-dir (str entry-id) "meta-data.json"))))))

(deftest download-step1-resolves-title-from-fake-api
  (require-fake!)
  (let [response (connection/connect-to-madek-server
                  {:body {:url fake-base-url
                          :api-token valid-token
                          :sign-in-method :token}})
        target (io/file (System/getProperty "java.io.tmpdir") "madek-step1-test")]
    (is (= 202 (:status response)))
    (let [out (web/download-step1 {:body {:entity-id media-entry-id
                                          :entity-type "entry"
                                          :url (str fake-base-url "/entries/" media-entry-id)
                                          :target-directory (.getAbsolutePath target)}})]
      (is (= 204 (:status out)))
      (is (= media-entry-id (get-in @state/db [:download :entity :uuid]))
          "step1 stores the media-entry id"))))
