(ns madek.app.front.download.progress-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [madek.app.front.download.progress :as progress]))

(def entry-id "00000000-0000-4000-8000-000000000010")
(def set-id "00000000-0000-4000-8000-000000000020")
(def base "https://madek.example")

(defn- entry
  [id & {:keys [file-progress state]}]
  (cond-> {:id id :type "MediaEntry"}
    (some? file-progress) (assoc :file-progress file-progress)
    state (assoc :state state)))

(defn- collection [id]
  {:id id :type "Collection" :progress 0.3 :state "downloading"})

(deftest item-public-id-strips-api-prefix
  (testing "dual-export keys are not part of the Medienarchiv path"
    (is (= entry-id (progress/item-public-id (str "api:" entry-id))))
    (is (= entry-id (progress/item-public-id (str "api-v2:" entry-id))))
    (is (= entry-id (progress/item-public-id entry-id)))))

(deftest medienarchiv-url-uses-public-id
  (is (= (str base "/entries/" entry-id)
         (progress/medienarchiv-url base (entry (str "api:" entry-id)))))
  (is (= (str base "/entries/" entry-id)
         (progress/medienarchiv-url base (entry (str "api-v2:" entry-id)))))
  (is (= (str base "/sets/" set-id)
         (progress/medienarchiv-url base (collection (str "api:" set-id)))))
  (is (nil? (progress/medienarchiv-url nil (entry entry-id))))
  (is (nil? (progress/medienarchiv-url "" (entry entry-id))))
  (is (nil? (progress/medienarchiv-url base {:id entry-id :type "Other"}))))

(deftest file-progress-ignores-sets
  (let [items [(collection (str "api:" set-id))
               (entry (str "api:" entry-id) :file-progress 1.0 :state "passed")
               (entry "00000000-0000-4000-8000-000000000021" :file-progress 0.5 :state "downloading")]
        stats (progress/file-progress-stats items)]
    (is (= 2 (:total stats)))
    (is (= 1 (:done stats)))
    (is (= 0.75 (:fraction stats)))))

(deftest passed-entry-without-file-progress-counts-as-done
  (let [stats (progress/file-progress-stats
               [(entry entry-id :state "passed")])]
    (is (= 1 (:done stats)))
    (is (= 1.0 (:fraction stats)))))

(deftest progress-percent-follows-file-bytes
  (testing "no media file yet, including a set that has already started"
    (is (= 0 (progress/progress-percent
              {:download-started true}
              [(collection set-id)]))))
  (testing "the first bytes of a file show as that fraction"
    (is (= 4 (progress/progress-percent
              {:download-started true}
              [(entry entry-id :file-progress 0.04)]))))
  (testing "sets do not dilute file progress"
    (is (= 50 (progress/progress-percent
               {:download-started true}
               [(collection set-id)
                (entry entry-id :file-progress 0.5)])))))

(deftest progress-percent-moves-half-a-point-per-request
  (testing "the first request is visible before any media file exists"
    (is (= 1 (progress/progress-percent
              {:download-started true :request-count 1}
              [(collection set-id)])))
    (is (integer? (progress/progress-percent
                   {:download-started true :request-count 1}
                   []))))
  (testing "half points round to a whole percent"
    (is (= 2 (progress/progress-percent
              {:download-started true :request-count 3}
              []))))
  (testing "file bytes take over once they are at least 1%, so the bar cannot sit on the request cycle"
    (is (= 4 (progress/progress-percent
              {:download-started true :request-count 199}
              [(entry entry-id :file-progress 0.04)])))
    (is (= 46 (progress/progress-percent
               {:download-started true :request-count 198}
               [(entry entry-id :file-progress 0.38)
                (entry "00000000-0000-4000-8000-000000000021" :file-progress 0.48)
                (entry "00000000-0000-4000-8000-000000000022" :file-progress 0.40)
                (entry "00000000-0000-4000-8000-000000000023" :file-progress 0.57)]))))
  (testing "requests still drive the bar before a file has visible bytes"
    (is (= 100 (progress/progress-percent
                {:download-started true :request-count 199}
                [(entry entry-id :file-progress 0.0)]))))
  (testing "the top of a cycle resets on the next request instead of sticking"
    (is (= 100 (progress/progress-percent
                {:download-started true :request-count 199}
                [])))
    (is (= 1 (progress/progress-percent
              {:download-started true :request-count 200}
              [])))
    (is (= 1 (progress/progress-percent
              {:download-started true :request-count 201}
              [])))
    (is (= 100 (progress/progress-percent
                {:download-started true :request-count 398}
                [])))
    (is (= 1 (progress/progress-percent
              {:download-started true :request-count 399}
              []))))
  (testing "logged lines count when no request counter is present"
    (is (= 1 (progress/progress-percent
                {:download-started true
                 :logs ["GET /a 200" "GET /b 200"]}
                [])))))

(deftest progress-percent-finished-and-cancelled
  (is (= 100 (progress/progress-percent
              {:download-started true :download-finished true}
              [(collection set-id)])))
  (is (= 100 (progress/progress-percent
              {:download-started true :download-finished true}
              [(entry entry-id :file-progress 1.0 :state "passed")])))
  (is (= 0 (progress/progress-percent
            {:download-started true :download-cancelled true}
            [])))
  (is (= 0 (progress/progress-percent {} [(entry entry-id :file-progress 0.0)]))))
