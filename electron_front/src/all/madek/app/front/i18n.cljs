(ns madek.app.front.i18n
  (:require
    [clojure.string :as string]
    [reagent.core :as reagent]
    [madek.app.front.i18n.messages :as messages]))

(def STORAGE-KEY "madek-exporter-locale")

(def LOCALES [:en :de :fr :it :es :zh])

(def LOCALE-LABELS
  {:en "EN"
   :de "DE"
   :fr "FR"
   :it "IT"
   :es "ES"
   :zh "ZH"})

(defonce locale* (reagent/atom :en))

(defn- interpolate [s args]
  (if (or (nil? args) (empty? args))
    s
    (reduce (fn [acc [k v]]
              (string/replace acc (str "{" (name k) "}") (str v)))
            s
            args)))

(defn t
  ([k] (t k nil))
  ([k args]
   (let [locale @locale*
         dict (get messages/dictionaries locale)
         en (get messages/dictionaries :en)
         raw (or (get dict k)
                 (get en k)
                 (str k))]
     (interpolate raw args))))

(defn- apply-document-lang! [locale]
  (when-let [el (.-documentElement js/document)]
    (.setAttribute el "lang" (name locale))))

(defn set-locale! [locale]
  (let [locale (keyword locale)]
    (when (contains? (set LOCALES) locale)
      (reset! locale* locale)
      (try
        (.setItem js/localStorage STORAGE-KEY (name locale))
        (catch :default _))
      (apply-document-lang! locale))))

(defn- from-storage []
  (try
    (when-let [stored (.getItem js/localStorage STORAGE-KEY)]
      (let [k (keyword stored)]
        (when (contains? (set LOCALES) k) k)))
    (catch :default _ nil)))

(defn- from-navigator []
  (try
    (let [nav (or (.-language js/navigator)
                  (first (array-seq (.-languages js/navigator)))
                  "")
          code (-> nav string/lower-case (string/split #"-") first)
          k (keyword code)]
      (when (contains? (set LOCALES) k) k))
    (catch :default _ nil)))

(defn init! []
  (let [locale (or (from-storage) (from-navigator) :en)]
    (reset! locale* locale)
    (apply-document-lang! locale)))
