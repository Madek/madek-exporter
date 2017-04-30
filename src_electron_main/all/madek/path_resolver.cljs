(ns madek.path-resolver
  (:require
    [cljs.nodejs :as nodejs]
    ))

(def path (nodejs/require "path"))

(def fs (nodejs/require "fs"))

(defn resolve-path [filename]
  (->> [(str "../" filename)
        (str "../../../" filename)]
       (map #(.resolve path (js* "__dirname") %))
       (filter #(.existsSync fs %))
       first))
