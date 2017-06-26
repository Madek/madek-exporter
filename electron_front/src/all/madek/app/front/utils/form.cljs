(ns madek.app.front.utils.form
  (:refer-clojure :exclude [str keyword])
  (:require
    [madek.app.front.utils :refer [str keyword deep-merge presence]]
    ))


(defn create-update-form-data-setter [state-db ks]
  (fn [k v]
    (swap! state-db
           assoc-in (conj ks k) v)))

