(ns madek.exporter.http-auth-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [madek.exporter.server.http-server :as http-server]
   [madek.exporter.utils :as utils]))

(defn- handler [_]
  {:status 200 :body "ok"})

(deftest basic-auth-rejects-missing-header
  (let [app (http-server/wrap-http-basic-auth handler "secret")
        resp (app {:headers {}})]
    (is (= 401 (:status resp)))))

(deftest basic-auth-rejects-wrong-password
  (let [app (http-server/wrap-http-basic-auth handler "secret")
        token (.encodeToString (java.util.Base64/getEncoder)
                               (.getBytes "x:wrong" "UTF-8"))
        resp (app {:headers {"authorization" (str "Basic " token)}})]
    (is (= 401 (:status resp)))))

(deftest basic-auth-accepts-password
  (let [app (http-server/wrap-http-basic-auth handler "secret")
        token (.encodeToString (java.util.Base64/getEncoder)
                               (.getBytes "client:secret" "UTF-8"))
        resp (app {:headers {"authorization" (str "Basic " token)}})]
    (is (= 200 (:status resp)))
    (is (= "ok" (:body resp)))))

(deftest safe-export-filename-strips-path-segments
  (is (= "photo.jpg"
         (utils/safe-export-filename "../../etc/passwd/photo.jpg" "id")))
  (is (= "id"
         (utils/safe-export-filename ".." "id")))
  (is (= "id"
         (utils/safe-export-filename "" "id"))))

(deftest validate-target-directory-rejects-relative
  (is (thrown-with-msg? clojure.lang.ExceptionInfo
                        #"absolute"
                        (utils/validate-target-directory "relative/path"))))

(deftest validate-target-directory-rejects-system-prefix
  (is (thrown-with-msg? clojure.lang.ExceptionInfo
                        #"system directory"
                        (utils/validate-target-directory "/usr/local/madek-export")))
  (is (thrown-with-msg? clojure.lang.ExceptionInfo
                        #"system directory"
                        (utils/validate-target-directory "/etc/madek"))))

(deftest client-safe-db-strips-http-options
  (let [db {:connection {:url "https://example.test"
                         :http-options {:basic-auth ["u" "p"]
                                        :headers {"authorization" "token x"}}}
            :download {:target-directory "/tmp"}}
        safe (utils/client-safe-db db)]
    (is (nil? (get-in safe [:connection :http-options])))
    (is (true? (get-in safe [:connection :authenticated?])))
    (is (= "https://example.test" (get-in safe [:connection :url])))))

(deftest safe-uri-to-open
  (is (true? (utils/safe-uri-to-open? "https://example.test/x" "/tmp")))
  (is (false? (utils/safe-uri-to-open? "javascript:alert(1)" "/tmp")))
  (testing "file under target"
    (let [target (System/getProperty "java.io.tmpdir")]
      (is (true? (utils/safe-uri-to-open?
                  (str "file://" target "/export/a.txt") target)))
      (is (false? (utils/safe-uri-to-open? "file:///etc/passwd" target))))))
