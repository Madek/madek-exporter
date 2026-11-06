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

(def percent-per-request
  "Points added to the bar for each completed Madek HTTP request."
  0.5)

(def ^:private requests-per-cycle
  "Requests in one pass: 0.5 points each, then the bar starts over.
  199 requests reach 99.5; the next request is 0.5 again."
  199)

(defn- round-percent
  "Nearest whole percent."
  [percent]
  (long (#?(:clj Math/round :cljs js/Math.round) (double percent))))

(defn request-progress-percent
  "0.5 points per completed request. After 99.5 the next request starts
  again at 0.5, so an open download does not sit at the end of the bar."
  [requests]
  (let [n (long (or requests 0))]
    (if (pos? n)
      (let [steps (mod (dec n) requests-per-cycle)]
        (/ (inc steps) 2.0))
      0)))

(defn progress-percent
  "Whole number 0–100 for the download bar.
  Before any file has a visible percent, each completed request
  (`:request-count` on `download`) adds 0.5 points, rounded to the nearest
  percent. After 199 requests (100) the next request resets that cycle to 1.
  Once media-file bytes round to at least 1%, the bar follows those bytes
  so it does not sit at the top of the request cycle during a transfer.
  A finished export with no media files is 100."
  [download items]
  (let [finished? (:download-finished download)
        {:keys [total fraction]} (file-progress-stats items)
        file-pct (round-percent (if (pos? total) (* 100.0 fraction) 0.0))
        requests (long (if (some? (:request-count download))
                         (:request-count download)
                         (count (or (:logs download) []))))]
    (cond
      (and finished? (zero? total)) 100
      finished? file-pct
      (pos? file-pct) file-pct
      (pos? requests) (round-percent (request-progress-percent requests))
      :else 0)))
