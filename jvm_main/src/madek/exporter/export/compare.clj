(ns madek.exporter.export.compare
  "Compare dual-export trees written by api-mode :both under `{target}/api`
  and `{target}/api-v2`.

  Checks:
  - files: every relative path in api/ must exist in api-v2/ (extra api-v2
    files are OK; directory symlinks are not followed)
  - json keys: for shared `*.json`, api-v2 must contain at least the same
    keys/structure as api (extra keys in api-v2 are OK). Key counts are
    logged per file."
  (:require
   [cheshire.core :as cheshire]
   [clojure.java.io :as io]
   [clojure.set :as set]
   [clojure.string :as string]
   [madek.exporter.state :as state])
  (:import
   [java.io File]
   [java.nio.file Files LinkOption Path]))

(def ^:private max-listed 100)

(defn- truncate-list [xs]
  (let [v (vec xs)]
    {:count (count v)
     :items (subvec v 0 (min (count v) max-listed))
     :truncated? (> (count v) max-listed)}))

(defn- rel-path [^Path root ^Path path]
  (-> root (.relativize path) str (string/replace #"\\" "/")))

(defn- list-regular-files
  "Relative paths of regular files under root. Does not follow symlinks."
  [^File root]
  (when (.isDirectory root)
    (let [root-path (.toPath root)
          no-follow (into-array LinkOption [LinkOption/NOFOLLOW_LINKS])]
      (->> (iterator-seq (.iterator (Files/walk root-path (into-array java.nio.file.FileVisitOption []))))
           (filter (fn [^Path p]
                     (Files/isRegularFile p no-follow)))
           (map #(rel-path root-path %))
           sort
           vec))))

(defn- key-str [k]
  (if (keyword? k) (name k) (str k)))

(defn- path-join [prefix segment]
  (if (string/blank? prefix)
    segment
    (str prefix "." segment)))

(defn- json-kind [x]
  (cond
    (map? x) :map
    (sequential? x) :seq
    :else :scalar))

(defn- count-keys
  "Number of nested map keys in parsed JSON (arrays are walked, scalars ignored)."
  [data]
  (cond
    (map? data)
    (+ (count data) (reduce + 0 (map count-keys (vals data))))

    (sequential? data)
    (reduce + 0 (map count-keys data))

    :else
    0))

(defn- parse-json-file [^File file]
  (try
    {:ok true :data (cheshire/parse-string (slurp file) true)}
    (catch Exception e
      {:ok false :error (.getMessage e)})))

(defn- check-structure
  "api-v2 must cover api keys/structure. Extra keys/elements in v2 are OK.
  Returns {:missing-keys [...] :broken-structure [...]}."
  ([api v2] (check-structure api v2 ""))
  ([api v2 prefix]
   (let [ka (json-kind api)
         kb (json-kind v2)]
     (cond
       (and (= ka :map) (= kb :map))
       (reduce (fn [acc [k v]]
                 (let [p (path-join prefix (key-str k))]
                   (if (contains? v2 k)
                     (merge-with into acc (check-structure v (get v2 k) p))
                     (update acc :missing-keys conj p))))
               {:missing-keys [] :broken-structure []}
               api)

       (and (= ka :seq) (= kb :seq))
       (let [va (vec api)
             vb (vec v2)]
         (reduce (fn [acc i]
                   (let [p (str prefix "[" i "]")]
                     (if (< i (count vb))
                       (merge-with into acc (check-structure (nth va i) (nth vb i) p))
                       (update acc :missing-keys conj p))))
                 {:missing-keys [] :broken-structure []}
                 (range (count va))))

       (= ka kb)
       {:missing-keys [] :broken-structure []}

       :else
       {:missing-keys []
        :broken-structure [(if (string/blank? prefix) "(root)" prefix)]}))))

(defn- compare-json-keys [^File api-file ^File v2-file]
  (let [a (parse-json-file api-file)
        b (parse-json-file v2-file)]
    (cond
      (not (:ok a))
      {:ok false
       :api-key-count 0
       :api-v2-key-count 0
       :missing-keys []
       :broken-structure ["(root)"]
       :error (str "api invalid JSON: " (:error a))}

      (not (:ok b))
      {:ok false
       :api-key-count (count-keys (:data a))
       :api-v2-key-count 0
       :missing-keys []
       :broken-structure ["(root)"]
       :error (str "api-v2 invalid JSON: " (:error b))}

      :else
      (let [{:keys [missing-keys broken-structure]}
            (check-structure (:data a) (:data b))
            missing (vec (sort missing-keys))
            broken (vec (sort broken-structure))]
        {:ok (and (empty? missing) (empty? broken))
         :api-key-count (count-keys (:data a))
         :api-v2-key-count (count-keys (:data b))
         :missing-keys missing
         :broken-structure broken}))))

(defn compare-trees
  "Compare `{target}/api` vs `{target}/api-v2`. Returns a result map."
  [target-dir]
  (let [api-root (io/file target-dir "api")
        v2-root (io/file target-dir "api-v2")
        api-exists? (.isDirectory api-root)
        v2-exists? (.isDirectory v2-root)]
    (cond
      (not api-exists?)
      {:ok false
       :equal? false
       :error (str "Missing api/ directory under " target-dir)}

      (not v2-exists?)
      {:ok false
       :equal? false
       :error (str "Missing api-v2/ directory under " target-dir)}

      :else
      (let [api-paths (list-regular-files api-root)
            v2-paths (list-regular-files v2-root)
            api-set (set api-paths)
            v2-set (set v2-paths)
            only-api (sort (set/difference api-set v2-set))
            only-v2 (sort (set/difference v2-set api-set))
            common (sort (set/intersection api-set v2-set))
            json-common (filter #(string/ends-with? % ".json") common)
            key-rows
            (mapv (fn [rel]
                    (let [cmp (compare-json-keys
                               (io/file api-root rel)
                               (io/file v2-root rel))]
                      (assoc cmp :path rel)))
                  json-common)
            mismatches (filterv (comp not :ok) key-rows)
            equal? (and (empty? only-api)
                        (empty? mismatches))]
        {:ok true
         :equal? equal?
         :api-root (.getAbsolutePath api-root)
         :api-v2-root (.getAbsolutePath v2-root)
         :counts {:api (count api-paths)
                  :api-v2 (count v2-paths)
                  :common (count common)
                  :only-in-api (count only-api)
                  :only-in-api-v2 (count only-v2)
                  :json-files (count json-common)
                  :api-keys (reduce + 0 (map :api-key-count key-rows))
                  :api-v2-keys (reduce + 0 (map :api-v2-key-count key-rows))
                  :key-mismatch (count mismatches)}
         :only-in-api (truncate-list only-api)
         :only-in-api-v2 (truncate-list only-v2)
         :key-counts
         (truncate-list
          (map (fn [row]
                 {:path (:path row)
                  :api-key-count (:api-key-count row)
                  :api-v2-key-count (:api-v2-key-count row)})
               key-rows))
         :key-mismatches
         (truncate-list
          (map (fn [row]
                 {:path (:path row)
                  :api-key-count (:api-key-count row)
                  :api-v2-key-count (:api-v2-key-count row)
                  :missing-keys (:missing-keys row)
                  :broken-structure (:broken-structure row)
                  :error (:error row)})
               mismatches))}))))

(defn compare-current-download!
  "Run comparison for the active download target and store under :data-check."
  []
  (let [target (get-in @state/db [:download :target-directory])
        result (if (string/blank? (str target))
                 {:ok false :equal? false :error "No target directory"}
                 (compare-trees target))]
    (swap! state/db assoc-in [:download :data-check] result)
    result))
