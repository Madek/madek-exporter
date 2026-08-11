(ns madek.exporter.export.index-html
  (:require
   [camel-snake-kebab.core :refer [->PascalCase]]
   [cheshire.core :as cheshire]
   [clojure.java.io :as io]
   [hiccup.core :as hiccup]
   [hiccup.page :refer [html5]]
   [logbug.catcher :as catcher]
   [logbug.debug :as debug :refer [I> I>>]]
   [logbug.thrown :as thrown]
   [madek.exporter.export.files :as files :refer [path-prefix]]
   [madek.exporter.state :as state]
   [madek.exporter.utils :refer [deep-merge]])

  (:import
   [java.io File]))

(defn title [media-resource]
  (str (-> media-resource :type name ->PascalCase)
       " "
       (-> media-resource :id)))

(defn url [media-resource]
  (let [url (str (-> @state/db :connection :url)
                 (case (:type media-resource)
                   :media-entry "/entries/"
                   :collection "/sets/")
                 (:id media-resource))]
    [:a
     {:href url}
     url]))

(defn blank-str? [s]
  (or (nil? s) (and (string? s) (clojure.string/blank? s))))

(defn present-str [s]
  (when-not (blank-str? s) (str s)))

(defn person-display-name
  "WebApp-style person label: first last [(pseudonym)]."
  [{:keys [first_name last_name pseudonym] :as person}]
  (let [full-name (->> [first_name last_name]
                       (keep present-str)
                       (clojure.string/join " ")
                       clojure.string/trim
                       present-str)
        pseudo (present-str pseudonym)]
    (cond
      (and full-name pseudo) (str full-name " (" pseudo ")")
      full-name full-name
      pseudo pseudo)))

(defn role-label [role]
  (when-let [labels (:labels role)]
    (or (present-str (:de labels))
        (present-str (:en labels))
        (->> labels vals (keep present-str) first))))

(defn person-value-entries
  "Prefer meta_data_people entries (nested :person); fall back to flat person docs."
  [values]
  (let [with-person (filter :person values)]
    (if (seq with-person)
      with-person
      values)))

(defn format-person-value [value]
  (let [person (or (:person value) value)
        display-name (person-display-name person)
        role (role-label (:role value))]
    (when display-name
      (if role
        (str display-name ": " role)
        display-name))))

(defn html-keywords-values [meta-datum]
  [:pre
   (->> (:values meta-datum)
        (map :term)
        (clojure.string/join ", "))])

(defn html-people-values [meta-datum]
  [:pre
   (->> (:values meta-datum)
        person-value-entries
        (keep format-person-value)
        (clojure.string/join ", "))])

(defn html-json-value [meta-datum]
  [:pre
   (cheshire/generate-string (:value meta-datum) {:pretty true})])

(defn html-generic [meta-datum]
  [:pre
   (cheshire/generate-string meta-datum {:pretty true})])

(defn meta-datum-present?
  "True when the meta-datum has something meaningful to show in HTML."
  [meta-datum]
  (case (:type meta-datum)
    ("MetaDatum::Text" "MetaDatum::TextDate")
    (present-str (:value meta-datum))

    "MetaDatum::Keywords"
    (seq (keep :term (:values meta-datum)))

    "MetaDatum::People"
    (seq (->> (:values meta-datum)
              person-value-entries
              (keep format-person-value)))

    "MetaDatum::JSON"
    (some? (:value meta-datum))

    ;; unknown types: keep only if there is a non-empty values collection or value
    (or (present-str (:value meta-datum))
        (seq (:values meta-datum)))))

(defn html-meta-datum [meta-datum]
  (let [meta-key-id (:meta_key_id meta-datum)]
    [:div.meta-datum {:class (:meta_key_id meta-datum)}
     [:h3 (or (:label meta-datum) meta-key-id)]
     (case (:type meta-datum)
       "MetaDatum::Keywords" (html-keywords-values meta-datum)
       "MetaDatum::People" (html-people-values meta-datum)
       ("MetaDatum::Text"
        "MetaDatum::TextDate") (:value meta-datum)
       "MetaDatum::JSON" (html-json-value meta-datum)
       (html-generic meta-datum))]))

(defn html-meta-data [meta-data]
  [:div.meta-data
   [:h1 "Meta-Data"]
   (doall (for [meta-datum meta-data
                :when (meta-datum-present? meta-datum)]
            (html-meta-datum meta-datum)))])

(defn html [media-resource meta-data]
  (html5
   [:head
    [:title (title media-resource)]
    [:meta {:charset "utf-8"}]
    [:body
     [:h2 (title media-resource)]
     [:p "URL: " (url media-resource)]
     (html-meta-data meta-data)]]))

(defn write [target-dir meta-data media-resource prefix-path]
  (io/make-parents target-dir)
  (let [html (html media-resource meta-data)]
    (doseq [path [(str target-dir File/separator "index.html")
                  (str target-dir File/separator prefix-path "_index.html")]]
      (spit path html))))

;### Debug ####################################################################
;(debug/re-apply-last-argument #'write)
;(debug/debug-ns *ns*)
