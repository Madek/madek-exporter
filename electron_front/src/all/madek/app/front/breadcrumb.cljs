(ns madek.app.front.breadcrumb)

(defn page-breadcrumb [& segments]
  [:p.page-breadcrumb
   (into [:span]
         (interpose " / "
                    (cons [:span.brand "Madek-Exporter"]
                          (map (fn [s] [:span.segment s]) segments))))])
