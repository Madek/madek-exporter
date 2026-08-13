(ns madek.exporter.server.web
  (:refer-clojure :exclude [str keyword])

  (:require

   [cheshire.core :as cheshire]
   [clj-http.client :as http-client]
   [clj-logging-config.log4j :as logging-config]
   [clojure.data.json :as json]

   [clojure.pprint :refer [pprint]]
   [clojure.tools.logging :as logging]
   [compojure.core :refer [ANY GET PATCH POST DELETE defroutes]]
   [compojure.route :refer [not-found resources]]
   [environ.core :refer [env]]
   [hiccup.core :refer [html]]
   [hiccup.page :refer [include-js include-css]]
   [inflections.core :refer [capitalize]]
   [json-roa.client.core :as roa]
   [logbug.catcher :as catcher]
   [logbug.debug :as debug :refer [I> I>>]]
   [logbug.ring :as logbug-ring :refer [wrap-handler-with-logging]]
   [logbug.thrown :as thrown]
   [madek.exporter.connection :as connection]

   [madek.exporter.export :as export]
   [madek.exporter.export.structure :as structure]
   [madek.exporter.state :as state]
   [madek.exporter.utils :as utils :refer [str keyword deep-merge presence]]
   [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
   [ring.middleware.json]
   [ring.util.response :refer [response]]))

;### download entity ##########################################################

(defn download-step1 [request]
  (catcher/snatch
   {:return-fn (fn [e] {:status 500 :body (thrown/stringify e)})}
   (let [http-options (-> @state/db :connection :http-options)
         id (-> request :body :entity-id)
         entity-type (case (-> request :body :entity-type)
                       "entry" :media-entry
                       "set" :collection)
         entity (-> (roa/get-root (str (-> @state/db :connection :url) "/api")
                                  :default-conn-opts (-> @state/db :connection :http-options))
                    (roa/relation entity-type)
                    (roa/get {:id id}))
         title (-> entity
                   (roa/relation :meta-data)
                   (roa/get {:meta_keys (json/write-str ["madek_core:title"])})
                   roa/coll-seq first (roa/get {}) roa/data :value)]
     (do (swap! state/db
                (fn [db dl-entity target-dir]
                  (deep-merge db
                              {:download
                               {:step1-completed true
                                :entity dl-entity
                                :target-directory target-dir}}))
                {:title title
                 :uuid id
                 :type entity-type
                 :url (-> request :body :url)}
                (-> request :body :target-directory))
         {:status 204}))))

;(pprint @state/db)

;### set options ##############################################################

(defn patch-download [request]
  (catcher/snatch
   {:return-fn (fn [e] {:status 500 :body (thrown/stringify e)})}
   (swap! state/db
          (fn [db body]
            (assoc-in db [:download]
                      (deep-merge (:download db)
                                  body)))
          (:body request))
   {:status 204}))

;##############################################################################

(defonce download-future (atom nil))

(defn- download-cancelled? []
  (boolean (get-in @state/db [:download :cancel-requested])))

(defn- mark-download-cancelled! []
  (swap! state/db
         (fn [db]
           (deep-merge db
                       {:download
                        {:download-finished true
                         :download-cancelled true
                         :cancel-requested true}}))))

(defn- mark-download-failed! [e]
  (swap! state/db
         (fn [db e]
           (deep-merge db
                       {:download
                        {:download-finished true
                         :errors {:dowload-error (str e)}}}))
         e))

(defn- stop-download-future!
  "Signal cancel and drop the future handle so a new run can start cleanly."
  []
  (when-let [f @download-future]
    (when-not (realized? f)
      (future-cancel f))
    (reset! download-future nil)))

(defn- reset-download-progress!
  "Hard-clear progress so a restart never reuses stale items/errors."
  []
  (swap! state/db
         (fn [db]
           (-> db
               (assoc-in [:download :download-started] true)
               (assoc-in [:download :download-finished] false)
               (assoc-in [:download :download-cancelled] false)
               (assoc-in [:download :cancel-requested] false)
               (assoc-in [:download :errors] nil)
               (assoc-in [:download :items] {})))))

(def snatch-dl-exception-params
  {:return-fn (fn [e]
                (if (download-cancelled?)
                  (mark-download-cancelled!)
                  (mark-download-failed! e)))
   :throwable Throwable})

(defn start-download-future [id target-dir recursive? skip-media-files? prefix-meta-key
                             export-structure entry-point http-options]
  (let [owned (atom nil)
        still-current? (fn [] (identical? @owned @download-future))
        f (future
            (catcher/snatch
             {:return-fn (fn [e]
                           (when (still-current?)
                             (if (or (download-cancelled?)
                                     (= :download-cancelled (:type (ex-data e))))
                               (mark-download-cancelled!)
                               (mark-download-failed! e))))
              :throwable Throwable}
             (case (-> @state/db :download :entity :type)
               :collection (export/download-set
                            id target-dir recursive? skip-media-files?
                            prefix-meta-key export-structure entry-point http-options)
               :media-entry (export/download-media-entry
                             id target-dir skip-media-files? prefix-meta-key
                             export-structure entry-point http-options))
             (when (still-current?)
               (if (download-cancelled?)
                 (mark-download-cancelled!)
                 (swap! state/db (fn [db] (assoc-in db [:download :download-finished] true)))))))]
    (reset! owned f)
    (reset! download-future f)))

(defn download [request]
  (catcher/snatch
   {:return-fn (fn [e]
                 (mark-download-failed! e)
                 {:status 500 :body (thrown/stringify e)})
    :throwable Throwable}
   (when @download-future
     (swap! state/db assoc-in [:download :cancel-requested] true)
     (stop-download-future!))
   (reset-download-progress!)
   (let [id (-> @state/db :download :entity :uuid)
         target-dir (-> @state/db :download :target-directory)
         recursive? (-> @state/db :download :recursive not not)
         skip-media-files? (-> @state/db :download :skip_media_files not not)
         download-meta-data-schema? true
         prefix-meta-key (-> @state/db :download :prefix_meta_key presence)
         export-structure (structure/normalize
                           (-> @state/db :download :export_structure))
         entry-point (state/connection-entry-point)
         http-options (state/connection-http-options)]
     (when download-meta-data-schema?
       (export/download-meta-data-schema target-dir))
     (start-download-future id target-dir recursive? skip-media-files? prefix-meta-key
                            export-structure entry-point http-options))
   {:status 202}))

(defn cancel-download [_]
  (swap! state/db assoc-in [:download :cancel-requested] true)
  (stop-download-future!)
  (mark-download-cancelled!)
  {:status 204})

(defn patch-download-item [request]
  (logging/debug 'patch-download-item {:request request})
  (let [item-id (-> request :route-params :id)
        patch-params (-> request :body)]
    (swap! state/db
           (fn [db item-id patch-params]
             (let [item-params (or (-> db :download :items (get item-id)) {})]
               (assoc-in db [:download :items item-id]
                         (deep-merge item-params
                                     patch-params))))
           item-id patch-params))
  {:status 204})

(defn delete-download [_]
  (swap! state/db (fn [db]
                    (dissoc db :download :download-entity)))
  {:status 204})

;##############################################################################

(defn patch-download-parameters [request]
  (logging/debug 'patch-download-parameters {:request request})
  (swap! state/db
         (fn [db params]
           (deep-merge db
                       {:download-parameters params}))
         (:body request))
  {:status 204})

;##############################################################################

(defn open [request]
  (logging/debug request)
  (utils/os-browse (-> request :body :uri))
  {:status 201})

;##############################################################################

(defn shutdown [request]
  (future
    (Thread/sleep 3000)
    (System/exit 0))
  "Good Bye!")

;##############################################################################

(defn vocabularies [_]
  (catcher/snatch
   {:return-fn (fn [e] {:status 500 :body (thrown/stringify e)})}
   (let [http-options (-> @state/db :connection :http-options)
         vocabularies (->> (-> (roa/get-root (str (-> @state/db :connection :url) "/api")
                                             :default-conn-opts (-> @state/db :connection :http-options))
                               (roa/relation :vocabularies)
                               (roa/get {})
                               roa/coll-seq)
                           (map #(roa/get % {}))
                           (map roa/data))]
     (response vocabularies))))

(defn meta-keys [vocabulary]
  (catcher/snatch
   {:return-fn (fn [e] {:status 500 :body (thrown/stringify e)})}
   (let [http-options (-> @state/db :connection :http-options)
         meta-keys (->> (-> (roa/get-root (str (-> @state/db :connection :url) "/api")
                                          :default-conn-opts (-> @state/db :connection :http-options))
                            (roa/relation :meta-keys)
                            (roa/get {:vocabulary vocabulary})
                            roa/coll-seq)
                        (map #(roa/get % {}))
                        (map roa/data))]
     (response meta-keys))))

;##############################################################################

(defroutes routes

  (DELETE "/download" _ #'delete-download)

  (POST "/connect" _ #'connection/connect-to-madek-server)
  (DELETE "/connect" _ #'connection/disconnect)

  (POST "/open" _ #'open)

  (PATCH "/download" _ #'patch-download)

  (PATCH "/download/items/:id" _ #'patch-download-item)

  (PATCH "/download-parameters" _ #'patch-download-parameters)

  (POST "/download" _ #'download)

  (POST "/download/cancel" _ #'cancel-download)

  (POST "/download/step1" _ #'download-step1)

  (ANY "/shutdown" _ #'shutdown)

  (GET "/vocabularies/" _ #'vocabularies)
  (GET "/vocabularies/:vocabulary/meta-keys/"
    [vocabulary] (#'meta-keys vocabulary))

  (resources "/")

  (not-found "Not Found"))

(def app
  (I> wrap-handler-with-logging
      routes
      state/wrap
      ring.middleware.json/wrap-json-response
      (wrap-defaults (assoc-in site-defaults [:security :anti-forgery] false))
      (ring.middleware.json/wrap-json-body {:keywords? true})
      ring.middleware.keyword-params/wrap-keyword-params
      ring.middleware.params/wrap-params))

;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'ring.middleware.resource)
;(debug/debug-ns *ns*)
