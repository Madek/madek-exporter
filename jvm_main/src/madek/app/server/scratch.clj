(ns madek.app.server.scratch
  (:require
    [clojure.data :refer :all]
    [timothypratley.patchin :as patchin]
    ))


(patchin/diff {:ar [1 1 3 4 5 6] :x :y :a {:b 7 :c {:d 42       :k :keep :e :removed}}}
              {:ar [1 2 3 4 5 6] :x :y :a {:b 7 :c {:d :changed :k :keep }}})



