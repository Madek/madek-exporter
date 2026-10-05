(ns madek.exporter.api
  "API mode helpers: dual status probes live in connection.clj; export target
  layout and entry-points are resolved here from :api-mode."
  (:require
   [madek.exporter.state :as state])
  (:import
   [java.io File]))

(def ^:dynamic *export-api-label*
  "When bound (e.g. :api or :api-v2), item ids in download state are namespaced
  so dual exports do not collide / symlink each other."
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

(defn entry-point
  "HTTP/ROA base for export fetches.

  NOTE: Export still uses the JSON-ROA client against `/api` for every mode.
  Mode only selects the output directory layout until the api-v2 REST exporter
  lands (see EXPORTER-APIV2-TODO.md §4–§5)."
  ([api-key]
   (entry-point (-> @state/db :connection :url) api-key))
  ([base-url api-key]
   (let [_ (normalize-mode api-key)]
     (str base-url "/api"))))

(defn export-runs
  "Build one or two export runs from api-mode.

  - :api / :api-v2 → single run into base-target
  - :both → two runs into `{base}/api` and `{base}/api-v2`"
  [base-url base-target api-mode]
  (let [mode (normalize-mode api-mode)
        sep File/separator
        run (fn [label target]
              {:label label
               :entry-point (entry-point base-url label)
               :target-dir target})]
    (case mode
      :api [(run :api base-target)]
      :api-v2 [(run :api-v2 base-target)]
      :both [(run :api (str base-target sep "api"))
             (run :api-v2 (str base-target sep "api-v2"))])))
