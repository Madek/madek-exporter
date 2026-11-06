(ns madek.app.front.download.progress
  "Download-view progress and Medienarchiv links.
  Item ids in download state are prefixed (`api:` / `api-v2:`) so a dual
  export does not collide. The bar counts media files, not sets.")

(defn item-public-id
  "Madek id for links. Strips an `api:` or `api-v2:` download-state prefix."
  [id]
  (let [s (str id)]
    (or (second (re-matches #"^(?:api-v2|api):(.+)$" s))
        s)))

(defn- medienarchiv-path [item-type]
  (case item-type
    "Collection" "/sets/"
    "MediaEntry" "/entries/"
    nil))

(defn medienarchiv-url
  "Absolute Medienarchiv URL for a download item, or nil when it cannot be built."
  [base-url item]
  (when-let [base (and (string? base-url)
                       (not (clojure.string/blank? base-url))
                       base-url)]
    (when-let [path (medienarchiv-path (:type item))]
      (str base path (item-public-id (:id item))))))

(defn- clamp01 [n]
  (max 0.0 (min 1.0 (double n))))

(defn item-file-progress
  "0.0–1.0 of this entry's media file. A passed entry with no fraction counts as done."
  [item]
  (cond
    (number? (:file-progress item)) (clamp01 (:file-progress item))
    (= "passed" (:state item)) 1.0
    :else 0.0))

(defn file-progress-stats
  "Progress across media files only. Each media entry is one file;
  in-progress bytes count partially. Collections are ignored."
  [items]
  (let [entries (vec (filter #(= "MediaEntry" (:type %)) items))
        total (count entries)
        sum (reduce + 0.0 (map item-file-progress entries))
        done (count (filter #(>= (item-file-progress %) 1.0) entries))]
    {:total total
     :done done
     :fraction (if (pos? total) (/ sum total) 0.0)}))

(defn- round-percent [fraction]
  (long (#?(:clj Math/round :cljs js/Math.round) (* 100.0 fraction))))

(defn progress-percent
  "0–100 for the download bar from media-file progress only.
  `download` is the :download map; `items` is a sequence of item maps.
  A finished export with no media files is 100. Otherwise a file that is
  only partly downloaded shows that fraction, including the first bytes."
  [download items]
  (let [finished? (:download-finished download)
        {:keys [total fraction]} (file-progress-stats items)]
    (cond
      (and finished? (zero? total)) 100
      (zero? total) 0
      :else (round-percent fraction))))
