(ns madek.exporter.connection-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is use-fixtures]]
   [madek.exporter.connection :as connection]
   [madek.exporter.state :as state]
   [madek.exporter.utils :as utils]))

(def ^:private fake-base-url (System/getenv "FAKE_MADEK_API_TEST_URL"))

(def ^:private valid-token "test-token")
(def ^:private valid-login "fake-user")
(def ^:private valid-email "fake-user@example.test")
(def ^:private account-password "fake-password")
(def ^:private api-client-login "fake-api-client")
(def ^:private api-client-password "fake-api-client-password")

(defn- connect-basic [login password]
  (connection/connect-to-madek-server
   {:body {:url fake-base-url
           :login login
           :password password
           :sign-in-method :login}}))

(defn- require-fake! []
  (when-not fake-base-url
    (throw (ex-info "Set FAKE_MADEK_API_TEST_URL (run ./bin/test-login)" {}))))

(defn clear-connection-fixture [f]
  (swap! state/db assoc :connection {})
  (f))

(use-fixtures :each clear-connection-fixture)

(deftest probe-token-succeeds-on-both-apis
  (require-fake!)
  (let [{:keys [api api-v2 availability]} (connection/probe-apis fake-base-url
                                                                  {:headers {"authorization"
                                                                             (str "token " valid-token)}})]
    (is (:available api))
    (is (= 200 (:status api)))
    (is (= "fake-user" (:login (:auth-info api))))
    (is (:available api-v2))
    (is (= 200 (:status api-v2)))
    (is (= :both availability))))

(deftest probe-invalid-token-fails-both-apis
  (require-fake!)
  (let [{:keys [api api-v2 availability]} (connection/probe-apis fake-base-url
                                                                  {:headers {"authorization"
                                                                             "token wrong-token"}})]
    (is (not (:available api)))
    (is (= :auth-failed (:reason api)))
    (is (not (:available api-v2)))
    (is (= :auth-failed (:reason api-v2)))
    (is (= :neither availability))))

(deftest probe-basic-succeeds-v1-only
  (require-fake!)
  (let [http-opts (utils/options-to-http-options {:login valid-login :password valid-token})
        {:keys [api api-v2 availability]} (connection/probe-apis fake-base-url http-opts)]
    (is (:available api))
    (is (not (:available api-v2)))
    (is (= :unsupported-auth (:reason api-v2)))
    (is (= :api availability))))

(deftest probe-basic-wrong-password-fails-v1
  (require-fake!)
  (let [{:keys [api availability]} (connection/probe-apis
                                     fake-base-url
                                     (utils/options-to-http-options {:login valid-login
                                                                     :password "wrong"}))]
    (is (not (:available api)))
    (is (= :auth-failed (:reason api)))
    (is (= :neither availability))))

(deftest connect-with-valid-token
  (require-fake!)
  (let [response (connection/connect-to-madek-server
                  {:body {:url fake-base-url
                          :api-token valid-token
                          :sign-in-method :token}})
        api-status (get-in response [:body :api-status])]
    (is (= 202 (:status response)))
    (is (= :both (:availability api-status)))
    (is (= fake-base-url (-> @state/db :connection :url)))
    (is (= "fake-user" (-> @state/db :connection :login)))))

(deftest connect-with-invalid-token
  (require-fake!)
  (let [response (connection/connect-to-madek-server
                  {:body {:url fake-base-url
                          :api-token "wrong"
                          :sign-in-method :token}})
        api-status (get-in response [:body :api-status])]
    (is (= 401 (:status response)))
    (is (not (:available (:api api-status))))))

(deftest connect-with-login-and-token
  (require-fake!)
  (let [response (connect-basic valid-login valid-token)
        api-status (get-in response [:body :api-status])]
    (is (= 202 (:status response)))
    (is (= :api (:availability api-status)))
    (is (:available (:api api-status)))
    (is (not (:available (:api-v2 api-status))))
    (is (= :unsupported-auth (:reason (:api-v2 api-status))))
    (is (= valid-login (-> @state/db :connection :login)))))

(deftest connect-with-email-and-token
  (require-fake!)
  (let [response (connect-basic valid-email valid-token)]
    (is (= 202 (:status response)))
    (is (= valid-login (-> @state/db :connection :login)))
    (is (= valid-email (-> @state/db :connection :email_address)))))

(deftest connect-with-login-and-account-password-rejected
  (require-fake!)
  (let [response (connect-basic valid-login account-password)]
    (is (= 401 (:status response)))
    (is (empty? (:connection @state/db)))))

(deftest connect-with-email-and-account-password-rejected
  (require-fake!)
  (let [response (connect-basic valid-email account-password)]
    (is (= 401 (:status response)))
    (is (empty? (:connection @state/db)))))

(deftest connect-with-api-client-and-password
  (require-fake!)
  (let [response (connect-basic api-client-login api-client-password)
        auth-info (-> @state/db :connection :auth-info)]
    (is (= 202 (:status response)))
    (is (= "ApiClient" (:type auth-info)))
    (is (= api-client-login (-> @state/db :connection :login)))))

(deftest connect-with-wrong-password
  (require-fake!)
  (let [response (connection/connect-to-madek-server
                  {:body {:url fake-base-url
                          :login valid-login
                          :password "wrong"
                          :sign-in-method :login}})]
    (is (= 401 (:status response)))))

(deftest connect-login-without-password
  (require-fake!)
  (let [response (connection/connect-to-madek-server
                  {:body {:url fake-base-url
                          :login valid-login
                          :sign-in-method :login}})]
    (is (= 422 (:status response)))))

(deftest basic-auth-only-predicate
  (is (connection/basic-auth-only?
       {:basic-auth ["u" "p"]
        :headers {"authorization" "Basic x="}}))
  (is (not (connection/basic-auth-only?
            {:headers {"authorization" "token t"}}))))

(deftest probe-without-credentials
  (require-fake!)
  (let [{:keys [api api-v2 availability]} (connection/probe-apis fake-base-url {})]
    (is (not (:available api)))
    (is (= 401 (:status api)))
    (is (= :auth-failed (:reason api)))
    (is (not (:available api-v2)))
    (is (= :neither availability))))

(deftest connect-missing-url
  (let [response (connection/connect-to-madek-server
                  {:body {:api-token valid-token :sign-in-method :token}})]
    (is (= 422 (:status response)))
    (is (re-find #"url" (str/lower-case (get-in response [:body :message]))))))

(deftest connect-unreachable-host
  (let [dead-port (+ 59000 (mod (hash (str (System/nanoTime))) 5000))
        dead-url (str "http://127.0.0.1:" dead-port)
        response (connection/connect-to-madek-server
                  {:body {:url dead-url
                          :api-token valid-token
                          :sign-in-method :token}})]
    (is (contains? #{422 502} (:status response)))
    (is (string? (get-in response [:body :message])))))

(deftest connect-without-credentials
  (require-fake!)
  (let [response (connection/connect-to-madek-server
                  {:body {:url fake-base-url :sign-in-method :token}})]
    (is (= 401 (:status response))
        "empty token must not connect")))
