(ns madek.exporter.connection-v1-only-test
  "Run against fake-madek-api with FAKE_MADEK_API_V2_DISABLED=1 (see bin/test-login)."
  (:require
   [clojure.test :refer [deftest is use-fixtures]]
   [madek.exporter.connection :as connection]
   [madek.exporter.state :as state]))

(def ^:private fake-base-url (System/getenv "FAKE_MADEK_API_TEST_URL"))
(def ^:private valid-token "test-token")

(defn clear-connection-fixture [f]
  (swap! state/db assoc :connection {})
  (f))

(use-fixtures :each clear-connection-fixture)

(deftest probe-token-succeeds-v1-only
  (when-not fake-base-url
    (throw (ex-info "Set FAKE_MADEK_API_TEST_URL" {})))
  (let [{:keys [api api-v2 availability]} (connection/probe-apis
                                           fake-base-url
                                           {:headers {"authorization"
                                                      (str "token " valid-token)}})]
    (is (:available api))
    (is (not (:available api-v2)))
    (is (= :not-found (:reason api-v2)))
    (is (= :api availability))))

(deftest connect-token-stores-v1-only-api-status
  (when-not fake-base-url
    (throw (ex-info "Set FAKE_MADEK_API_TEST_URL" {})))
  (let [response (connection/connect-to-madek-server
                  {:body {:url fake-base-url
                          :api-token valid-token
                          :sign-in-method :token}})
        api-status (get-in response [:body :api-status])]
    (is (= 202 (:status response)))
    (is (= :api (:availability api-status)))
    (is (= :api (get-in @state/db [:connection :api-status :availability])))))
