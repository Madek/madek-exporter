(ns madek.exporter.api
  "API mode helpers: dual status probes live in connection.clj; export target
  layout and entry-points are resolved here from :api-mode."
  (:require
   [clojure.string :as string]
   [madek.exporter.state :as state])
  (:import
   [java.io File]))

(def ^:dynamic *export-api-label*
  "When bound (e.g. :api or :api-v2), item ids in download state are namespaced
  so dual exports do not collide. Must also be re-bound on worker threads
  (see export/run-bounded!). Symlinks stay inside one tree only."
  nil)

(defn normalize-mode [api-mode]
  (let [mode (keyword (or api-mode :api))]
    (if (#{:api :api-v2 :both} mode) mode :api)))

(defn current-mode []
  (normalize-mode (-> @state/db :connection :api-mode)))

(defn scoped-item-id
  "Namespace item ids when exporting under an API label (both mode)."
  [id]
  (let [id (str id)]
    (if-let [label *export-api-label*]
      (str (name label) ":" id)
      id)))

(defn- strip-trailing-slashes [url]
  (string/replace (str url) #"/+$" ""))

(defn entry-point
  "HTTP base for one export run.

  - :api → `{base}/api` (JSON-ROA)
  - :api-v2 → `{base}/api-v2` (plain JSON)"
  ([api-key]
   (entry-point (-> @state/db :connection :url) api-key))
  ([base-url api-key]
   (let [base (strip-trailing-slashes base-url)]
     (case (normalize-mode api-key)
       :api-v2 (str base "/api-v2")
       (str base "/api")))))

(defn export-runs
  "Build one or two export runs from api-mode.

  - :api → JSON-ROA into base-target
  - :api-v2 → REST into base-target
  - :both → `{base}/api` (ROA) and `{base}/api-v2` (REST)"
  [base-url base-target api-mode]
  (let [mode (normalize-mode api-mode)
        sep File/separator
        run (fn [label target]
              {:label label
               :transport (if (= label :api-v2) :rest :roa)
               :entry-point (entry-point base-url label)
               :target-dir target})]
    (case mode
      :api [(run :api base-target)]
      :api-v2 [(run :api-v2 base-target)]
      :both [(run :api (str base-target sep "api"))
             (run :api-v2 (str base-target sep "api-v2"))])))
