(ns madek.exporter.export-fake-api-test
  (:require
   [clj-http.client :as http]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [clojure.test :refer [deftest is use-fixtures]]
   [madek.exporter.api :as api]
   [madek.exporter.connection :as connection]
   [madek.exporter.export :as export]
   [madek.exporter.export.meta-data-schema :as export-meta-data-schema]
   [madek.exporter.export.meta-keys :as export-meta-keys]
   [madek.exporter.perf-log :as perf-log]
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
  {:media-entry-metadata 14
   :media-entry-with-file 16
   :set-metadata 25
   :media-entry-both-apis 19})

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
   & {:keys [api-mode prefix-meta-key] :or {api-mode :api}}]
  (let [target-dir (doto target-dir (.mkdirs) (.deleteOnExit))]
    (connect-fake! :api-mode api-mode)
    (is (= 204 (:status (web/download-step1
                         {:body {:entity-id entity-id
                                 :entity-type entity-type
                                 :url (str fake-base-url url-suffix)
                                 :target-directory (.getAbsolutePath target-dir)}}))))
    (is (= 204 (:status (web/patch-download
                         {:body (cond-> {:step2-completed true
                                         :skip_media_files skip-media-files?
                                         :recursive false}
                                  (some? prefix-meta-key)
                                  (assoc :prefix_meta_key prefix-meta-key))}))))
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

(defn- export-entry-folder! [prefix-meta-key]
  (let [target-dir (io/file (System/getProperty "java.io.tmpdir")
                            (str "madek-web-entry-prefix-" (System/nanoTime)))]
    (web-export-wait! media-entry-id "entry"
                      (str "/entries/" media-entry-id)
                      target-dir true
                      :prefix-meta-key prefix-meta-key)
    target-dir))

(deftest web-download-prefix-with-value-uses-title
  (let [target-dir (export-entry-folder! "madek_core:title")
        folder (str "Fake export entry_" media-entry-id)]
    (is (.exists (io/file target-dir folder "meta-data.json")))
    (is (not (.exists (io/file target-dir media-entry-id))))))

(deftest web-download-prefix-without-value-uses-underscore
  (let [target-dir (export-entry-folder! "madek_core:subtitle")
        folder (str "_" media-entry-id)]
    (is (.exists (io/file target-dir folder "meta-data.json")))
    (is (not (.exists (io/file target-dir media-entry-id))))))

(deftest web-download-no-prefix-uses-bare-id
  (let [target-dir (export-entry-folder! "")]
    (is (.exists (io/file target-dir media-entry-id "meta-data.json")))
    (is (not (.exists (io/file target-dir (str "Fake export entry_" media-entry-id)))))
    (is (not (.exists (io/file target-dir (str "_" media-entry-id)))))))

(defn- clear-fake-requests!
  []
  (require-fake!)
  (let [res (http/delete (str fake-base-url "/_test/requests")
                         {:as :json :throw-exceptions false})]
    (is (= 200 (:status res)) "clear fake request log")))

(defn- fake-request-paths []
  (require-fake!)
  (let [res (http/get (str fake-base-url "/_test/requests")
                      {:as :json :throw-exceptions false})]
    (is (= 200 (:status res)))
    (->> (get-in res [:body :requests])
         (map :path)
         vec)))

(defn- api-v1-path? [path]
  (boolean (re-find #"^/api(/|$)" (str path))))

(defn- api-v2-path? [path]
  (boolean (re-find #"^/api-v2(/|$)" (str path))))

(defn- step1-entry! []
  (is (= 204 (:status (web/download-step1
                       {:body {:entity-id media-entry-id
                               :entity-type "entry"
                               :url (str fake-base-url "/entries/" media-entry-id)
                               :target-directory (System/getProperty "java.io.tmpdir")}})))))

(defn- step1-set! []
  (is (= 204 (:status (web/download-step1
                       {:body {:entity-id set-id
                               :entity-type "set"
                               :url (str fake-base-url "/sets/" set-id)
                               :target-directory (System/getProperty "java.io.tmpdir")}})))))

(deftest prefix-options-lists-title-with-folder-name
  (connect-fake!)
  (step1-entry!)
  (let [{:keys [status body]} (web/prefix-options {})
        title (some #(when (= "madek_core:title" (:meta_key_id %)) %) (:options body))
        subtitle (some #(when (= "madek_core:subtitle" (:meta_key_id %)) %) (:options body))]
    (is (= 200 status))
    (is (= media-entry-id (:entity_id body)))
    (is (= 2 (count (:options body)))
        "all text meta-keys (title with value, subtitle empty)")
    (is (= "Madek Core" (:vocabulary_label title)))
    (is (= "Title" (:meta_key_label title)))
    (is (= "Fake export entry" (:value title)))
    (is (= (str "Fake export entry_" media-entry-id) (:folder_name title)))
    (is (nil? (:value subtitle)))
    (is (= (str "_" media-entry-id) (:folder_name subtitle)))
    (is (some #(and (= :madek (:kind %))
                    (re-find #"/api/meta-keys/" (:url %))
                    (= 200 (:status %))
                    (integer? (:ms %)))
              (perf-log/entries))
        "Madek calls are recorded for the Debug → Performance tab")))
(defn- assert-paths-for-api!
  [expected-api paths context]
  (is (seq paths) (str context " hits the fake server"))
  (case expected-api
    :api
    (do
      (is (every? api-v1-path? paths)
          (str context " must use /api only, got: " paths))
      (is (not-any? api-v2-path? paths)
          (str context " must not touch /api-v2, got: " paths)))
    :api-v2
    (do
      (is (every? api-v2-path? paths)
          (str context " must use /api-v2 only, got: " paths))
      (is (not-any? api-v1-path? paths)
          (str context " must not touch /api, got: " paths)))
    :both
    (do
      (is (some api-v1-path? paths)
          (str context " must hit /api, got: " paths))
      (is (some api-v2-path? paths)
          (str context " must hit /api-v2, got: " paths)))))

(defn- assert-prefix-options-paths!
  "Assert prefix-options hit the expected Madek API and return the body.
  Optional `request` may include `:params` / `:query-params` for `?api-mode=`."
  ([expected-api]
   (assert-prefix-options-paths! expected-api {}))
  ([expected-api request]
   (clear-fake-requests!)
   (let [{:keys [status body]} (web/prefix-options request)
         after-prefix (fake-request-paths)
         title (some #(when (= "madek_core:title" (:meta_key_id %)) %)
                     (:options body))]
     (is (= 200 status))
     (is (= 2 (count (:options body)))
         "title (with value) + subtitle (empty) from the selected API")
     (is (= "Fake export entry" (:value title)))
     (assert-paths-for-api! expected-api after-prefix "prefix-options")
     (is (some #(string/includes? % "meta-keys") after-prefix)
         "prefix-options loads meta-keys")
     body)))

(deftest connect-stores-selected-api-mode
  (doseq [mode [:api :api-v2 :both]]
    (connect-fake! :api-mode mode)
    (is (= mode (api/current-mode))
        (str "connect with api-mode " mode))))

(deftest prefix-options-uses-api-when-mode-is-api
  (connect-fake! :api-mode :api)
  (step1-entry!)
  (assert-prefix-options-paths! :api))

(deftest prefix-options-uses-api-when-both-apis-selected
  ;; Mode :both exports into api/ + api-v2/, but the prefix picker still
  ;; walks Madek `/api` only.
  (connect-fake! :api-mode :both)
  (clear-fake-requests!)
  (step1-entry!)
  (let [after-step1 (fake-request-paths)]
    (assert-paths-for-api! :api after-step1 "step1"))
  (assert-prefix-options-paths! :api))

(deftest prefix-options-uses-api-v2-when-mode-is-api-v2
  (connect-fake! :api-mode :api-v2)
  (step1-entry!)
  (assert-prefix-options-paths! :api-v2))

(deftest prefix-options-query-param-overrides-connection-mode
  ;; UI sends ?api-mode= so the walk matches the picker even if connection
  ;; state still has another mode.
  (connect-fake! :api-mode :api)
  (step1-entry!)
  (assert-prefix-options-paths! :api-v2 {:params {:api-mode "api-v2"}})
  (assert-prefix-options-paths! :api {:params {:api-mode "both"}})
  (connect-fake! :api-mode :api-v2)
  (step1-entry!)
  (assert-prefix-options-paths! :api {:params {:api-mode "api"}}))

(deftest prefix-options-uses-api-for-set-when-both-apis-selected
  (connect-fake! :api-mode :both)
  (clear-fake-requests!)
  (step1-set!)
  (let [after-step1 (fake-request-paths)]
    (assert-paths-for-api! :api after-step1 "set step1")
    (is (some #(string/includes? % "collections") after-step1)
        "set step1 uses /api/collections"))
  (clear-fake-requests!)
  (let [{:keys [status]} (web/prefix-options {})
        after-prefix (fake-request-paths)]
    (is (= 200 status))
    (assert-paths-for-api! :api after-prefix "set prefix-options")))

(deftest prefix-options-uses-api-v2-for-set-when-mode-is-api-v2
  (connect-fake! :api-mode :api-v2)
  (step1-set!)
  (clear-fake-requests!)
  (let [{:keys [status body]} (web/prefix-options {})
        after-prefix (fake-request-paths)]
    (is (= 200 status))
    (is (= 2 (count (:options body))))
    (assert-paths-for-api! :api-v2 after-prefix "set prefix-options")
    (is (some #(string/includes? % "collections") after-prefix)
        "set prefix-options loads collection meta-data via /api-v2")))

(defn- export-paths-only!
  "Connect + step1 + step2, then clear the fake log and run export only."
  [api-mode]
  (let [target-dir (doto (io/file (System/getProperty "java.io.tmpdir")
                                  (str "madek-export-paths-" (name api-mode)
                                       "-" (System/nanoTime)))
                     (.mkdirs)
                     (.deleteOnExit))]
    (connect-fake! :api-mode api-mode)
    (is (= 204 (:status (web/download-step1
                         {:body {:entity-id media-entry-id
                                 :entity-type "entry"
                                 :url (str fake-base-url "/entries/" media-entry-id)
                                 :target-directory (.getAbsolutePath target-dir)}}))))
    (is (= 204 (:status (web/patch-download
                         {:body {:step2-completed true
                                 :skip_media_files true
                                 :recursive false}}))))
    (clear-fake-requests!)
    (is (= 202 (:status (web/download {}))))
    (loop [n 0]
      (cond
        (get-in @state/db [:download :download-finished])
        (fake-request-paths)
        (> n 200) (do (is false "export did not finish in time") [])
        :else (do (Thread/sleep 100) (recur (inc n)))))))

(deftest export-media-entry-hits-only-api-when-mode-is-api
  (let [paths (export-paths-only! :api)]
    (assert-paths-for-api! :api paths "export")
    (is (some #(string/includes? % "media-entries") paths))))

(deftest export-media-entry-hits-only-api-v2-when-mode-is-api-v2
  (let [paths (export-paths-only! :api-v2)]
    (assert-paths-for-api! :api-v2 paths "export")
    (is (some #(string/includes? % "media-entries") paths))))

(deftest export-media-entry-hits-both-apis-when-mode-is-both
  (let [paths (export-paths-only! :both)]
    (assert-paths-for-api! :both paths "export")
    (is (some #(and (api-v1-path? %) (string/includes? % "media-entries")) paths))
    (is (some #(and (api-v2-path? %) (string/includes? % "media-entries")) paths))))

(deftest prefix-options-without-step1
  (is (= 409 (:status (web/prefix-options {})))))

(deftest continuing-to-step-3-aborts-prefix-options
  (let [gen (web/abort-prefix-options!)]
    (is (not (web/prefix-options-aborted? gen)))
    (is (= 204 (:status (web/patch-download {:body {:recursive true}}))))
    (is (not (web/prefix-options-aborted? gen)))
    (is (= 204 (:status (web/patch-download {:body {:step2-completed true}}))))
    (is (web/prefix-options-aborted? gen)))
  (let [gen (web/abort-prefix-options!)]
    (is (= 204 (:status (web/patch-download {:body {:step1-completed false}}))))
    (is (web/prefix-options-aborted? gen))))
