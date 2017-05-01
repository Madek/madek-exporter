(ns madek-front.init
    (:require [madek-front.core :as core]
              [madek-front.conf :as conf]))

(enable-console-print!)

(defn start-descjop! []
  (core/init! conf/setting))

(start-descjop!)
