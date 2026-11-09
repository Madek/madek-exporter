(ns madek.exporter.server.web
  (:refer-clojure :exclude [str keyword])

  (:require

   [clj-logging-config.log4j :as logging-config]
   [clojure.data.json :as json]
   [clojure.tools.logging :as logging]
   [compojure.core :refer [ANY GET PATCH POST DELETE defroutes]]
   [compojure.route :refer [not-found resources]]
   [json-roa.client.core :as roa]
   [logbug.catcher :as catcher]
   [logbug.debug :as debug :refer [I>]]
   [logbug.ring :as logbug-ring :refer [wrap-handler-with-logging]]
   [madek.exporter.api :as api]
   [madek.exporter.connection :as connection]
   [madek.exporter.export :as export]
   [madek.exporter.export.api-v2 :as export-api-v2]
   [madek.exporter.export.compare :as export-compare]
   [madek.exporter.export.meta-data-schema :as export-meta-data-schema]
   [madek.exporter.export.meta-keys :as export-meta-keys]
   [madek.exporter.export.control :as control]
   [madek.exporter.perf-log :as perf-log]
   [madek.exporter.request-log :as request-log]
   [madek.exporter.server.prefix-options :as prefix-options]
   [madek.exporter.state :as state]
   [madek.exporter.utils :as utils :refer [str keyword deep-merge presence]]
   [taoensso.timbre :refer [info]]
   [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
   [ring.middleware.json]
   [ring.util.response :refer [response]]))

;### download entity ##########################################################

(defn- client-error-body [e]
  (let [msg (or (ex-message e) "Internal error")
        typ (:type (ex-data e))]
    (logging/error e "request failed")
    (cond-> {:message msg}
      typ (assoc :type typ))))

(defn- coerce-target-directory! [path]
  (try
    (utils/validate-target-directory path)
    (catch clojure.lang.ExceptionInfo e
      (if (= :invalid-target-directory (:type (ex-data e)))
        (throw e)
        (throw e)))))

(defn download-step1
  "Resolve entity title via Madek `/api` (JSON-ROA) only — independent of
  export `:api-mode` (including `:both` / api + api-v2)."
  [request]
  (catcher/snatch
   {:return-fn (fn [e]
                 {:status (if (= :invalid-target-directory (:type (ex-data e))) 422 500)
                  :body (client-error-body e)})}
   (let [id (-> request :body :entity-id)
         target-dir (coerce-target-directory! (-> request :body :target-directory))
         entity-type (case (-> request :body :entity-type)
                       "entry" :media-entry
                       "set" :collection)
         entity (-> (roa/get-root (str (-> @state/db :connection :url) "/api")
                                  :default-conn-opts (state/connection-http-options)
                                  :default-middleware perf-log/middleware)
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
                target-dir)
         {:status 204}))))

(defn patch-download [request]
  (catcher/snatch
   {:return-fn (fn [e]
                 {:status (if (= :invalid-target-directory (:type (ex-data e))) 422 500)
                  :body (client-error-body e)})}
   (let [body (:body request)
         body (if (contains? body :target-directory)
                (assoc body :target-directory
                       (coerce-target-directory! (:target-directory body)))
                body)]
     (when (or (true? (:step2-completed body))
               (false? (:step1-completed body)))
       (prefix-options/abort!))
     (when (nil? (:logs body))
       (request-log/clear!))
     (swap! state/db
            (fn [db body]
              (assoc-in db [:download]
                          (deep-merge (:download db)
                                      body)))
            body))
   {:status 204}))

;##############################################################################

(defonce download-future (atom nil))

(def ^:private download-stop-await-ms 5000)

(defn- download-cancelled? []
  (boolean (get-in @state/db [:download :cancel-requested])))

(defn- notify-download-ui! []
  (state/republish-full-to-all-clients!)
  (state/broadcast-download-logs!))

(defn- mark-download-cancelled! []
  (swap! state/db
         (fn [db]
           (deep-merge db
                       {:download
                        {:download-finished true
                         :download-cancelled true
                         :cancel-requested true}})))
  (notify-download-ui!))

(defn- mark-download-failed! [e]
  (swap! state/db
         (fn [db e]
           (deep-merge db
                       {:download
                        {:download-finished true
                         :errors {:download-error (str e)}}}))
         e)
  (notify-download-ui!))

(defn- stop-download-work!
  "Cancel in-flight download work, await a brief shutdown, and recreate the
  HTTP pool so a restart is not starved by leaked connections."
  []
  (control/bump-generation!)
  (swap! state/db assoc-in [:download :cancel-requested] true)
  (export/abort-parallel-downloads!)
  (when-let [f @download-future]
    (when-not (realized? f)
      (future-cancel f))
    (try
      (deref f download-stop-await-ms ::timeout)
      (catch Exception _))
    (reset! download-future nil))
  (utils/reset-http-conn-manager!))

(defn- reset-download-progress!
  "Hard-clear progress so a restart never reuses stale items/errors.
  Bumps download-generation so the new run has a fresh token."
  []
  (request-log/clear!)
  (export-meta-data-schema/reset-cache!)
  (export-meta-keys/reset-cache!)
  (swap! state/db
         (fn [db]
           (-> db
               (update-in [:download :download-generation] (fnil inc 0))
               (assoc-in [:download :download-started] true)
               (assoc-in [:download :download-finished] false)
               (assoc-in [:download :download-cancelled] false)
               (assoc-in [:download :cancel-requested] false)
               (assoc-in [:download :errors] nil)
               (assoc-in [:download :items] {})
               (assoc-in [:download :data-check] nil)))))

(def snatch-dl-exception-params
  {:return-fn (fn [e]
                (if (download-cancelled?)
                  (mark-download-cancelled!)
                  (mark-download-failed! e)))
   :throwable Throwable})

(defn start-download-future [id runs recursive? skip-media-files? prefix-meta-key
                             http-options]
  (let [owned (atom nil)
        still-current? (fn [] (identical? @owned @download-future))
        gen (control/current-generation)
        entity-type (-> @state/db :download :entity :type)
        f (future
            (binding [control/*download-generation* gen]
              (catcher/snatch
               {:return-fn (fn [e]
                             (when (still-current?)
                               (if (or (download-cancelled?)
                                       (= :download-cancelled (:type (ex-data e))))
                                 (mark-download-cancelled!)
                                 (mark-download-failed! e))))
                :throwable Throwable}
               (doseq [run runs]
                 (control/ensure-not-cancelled!)
                 (info "export-run" {:label (:label run)
                                     :transport (:transport run)
                                     :target-dir (:target-dir run)
                                     :entry-point (:entry-point run)})
                 (binding [api/*export-api-label* (:label run)]
                   (case (:transport run)
                     :rest
                     (let [ctx (export-api-v2/context (:entry-point run) http-options)]
                       (case entity-type
                         :collection (export-api-v2/download-set
                                      ctx id (:target-dir run) recursive?
                                      skip-media-files? prefix-meta-key)
                         :media-entry (export-api-v2/download-media-entry
                                       ctx id (:target-dir run) skip-media-files?
                                       prefix-meta-key)))
                     ;; :roa (default)
                     (case entity-type
                       :collection (export/download-set
                                    id (:target-dir run) recursive? skip-media-files?
                                    prefix-meta-key (:entry-point run) http-options)
                       :media-entry (export/download-media-entry
                                     id (:target-dir run) skip-media-files? prefix-meta-key
                                     (:entry-point run) http-options)))))
               (when (still-current?)
                 (if (download-cancelled?)
                   (mark-download-cancelled!)
                   (do
                     (swap! state/db
                            (fn [db] (assoc-in db [:download :download-finished] true)))
                     (notify-download-ui!)))))))]
    (reset! owned f)
    (reset! download-future f)))

(defn- download-meta-data-schema-for-run! [run http-options]
  (case (:transport run)
    :rest (export-api-v2/download-meta-data-schema
           (:entry-point run) http-options (:target-dir run))
    (export/download-meta-data-schema (:target-dir run))))

(defn download [request]
  (catcher/snatch
   {:return-fn (fn [e]
                 (mark-download-failed! e)
                 {:status 500 :body (client-error-body e)})
    :throwable Throwable}
   (stop-download-work!)
   (reset-download-progress!)
   (let [id (-> @state/db :download :entity :uuid)
         target-dir (-> @state/db :download :target-directory)
         recursive? (-> @state/db :download :recursive not not)
         skip-media-files? (-> @state/db :download :skip_media_files not not)
         download-meta-data-schema? true
         prefix-meta-key (-> @state/db :download :prefix_meta_key presence)
         base-url (-> @state/db :connection :url)
         api-mode (api/current-mode)
         runs (api/export-runs base-url target-dir api-mode)
         ;; Capture pool after stop/reset so we use the fresh manager.
         http-options (state/connection-http-options)]
     (info "download" {:api-mode api-mode
                       :runs (mapv #(select-keys % [:label :transport :target-dir]) runs)})
     (when download-meta-data-schema?
       (doseq [run runs]
         (download-meta-data-schema-for-run! run http-options)))
     (start-download-future id runs recursive? skip-media-files? prefix-meta-key
                            http-options))
   {:status 202}))

(defn cancel-download [_]
  (stop-download-work!)
  (mark-download-cancelled!)
  {:status 204})

(defn compare-download-data [_]
  (catcher/snatch
   {:return-fn (fn [e] {:status 500 :body (client-error-body e)})
    :throwable Throwable}
   (let [mode (api/current-mode)]
     (if-not (= :both mode)
       {:status 422
        :body {:message "Data check is only available when API mode is “both”."
               :api-mode mode}}
       (let [result (export-compare/compare-current-download!)]
         {:status (if (:ok result) 200 422)
          :body result})))))

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
  (swap! state/db
         (fn [db]
           (-> db
               (dissoc :download)
               (assoc :download {:target-directory state/default-download-directory
                                 :state :step1}))))
  {:status 204})

;##############################################################################

(defn patch-download-parameters [request]
  (logging/debug 'patch-download-parameters {:path (:uri request)})
  (swap! state/db
         (fn [db params]
           (deep-merge db
                       {:download-parameters params}))
         (:body request))
  {:status 204})

;##############################################################################

(defn open [request]
  (let [uri (-> request :body :uri)
        target (get-in @state/db [:download :target-directory])]
    (if (utils/safe-uri-to-open? uri target)
      (do (utils/os-browse uri)
          {:status 201})
      {:status 422
       :body {:message "URI scheme not allowed."}})))

;##############################################################################

(defn shutdown [_]
  (future
    (Thread/sleep 3000)
    (System/exit 0))
  {:status 202 :body {:message "Shutting down."}})

(defroutes routes

  (DELETE "/download" _ #'delete-download)

  (POST "/connect" _ #'connection/connect-to-madek-server)
  (POST "/connect/api-mode" _ #'connection/patch-api-mode)
  (PATCH "/connect/api-mode" _ #'connection/patch-api-mode)
  (DELETE "/connect" _ #'connection/disconnect)

  (POST "/open" _ #'open)

  (PATCH "/download" _ #'patch-download)

  (PATCH "/download/items/:id" _ #'patch-download-item)

  (PATCH "/download-parameters" _ #'patch-download-parameters)

  (POST "/download" _ #'download)

  (POST "/download/cancel" _ #'cancel-download)

  (POST "/download/compare" _ #'compare-download-data)

  (POST "/download/step1" _ #'download-step1)

  (GET "/download/prefix-options" _ #'prefix-options/handler)

  (GET "/debug/performance" _ (response {:entries (perf-log/entries)}))

  (DELETE "/debug/performance" _ (do (perf-log/clear!) {:status 204}))

  (ANY "/shutdown" _ #'shutdown)

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
      ring.middleware.params/wrap-params
      perf-log/wrap-ring))

;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'ring.middleware.resource)
;(debug/debug-ns *ns*)
