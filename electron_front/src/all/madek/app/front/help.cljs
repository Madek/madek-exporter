(ns madek.app.front.help
  (:require
    [clojure.string :as string]
    [madek.app.front.breadcrumb :as breadcrumb]
    [madek.app.front.i18n :as i18n]
    [madek.app.front.release :as release]
    [madek.app.front.state :as state]
    [cljs.nodejs :as nodejs]
    ))

(def Electron (nodejs/require "electron"))

(def shell (.-shell Electron))

(def prefix-examples-id "prefix-examples")

(defonce selected-section* (atom "help-version"))

(defonce toc-collapsed* (atom false))

(defonce scroll-to* (atom nil))

(defn- consume-requested-section! []
  (when-let [id @scroll-to*]
    (reset! scroll-to* nil)
    (reset! selected-section* id)
    (reset! toc-collapsed* false)))

(defn- external-link
  ([url] (external-link url (string/replace url #"^https://" "")))
  ([url label]
   [:a {:href url
        :on-click (fn [e]
                    (.preventDefault e)
                    (.openExternal shell url))}
    label]))

(defn full-version []
  (when-let [rel (-> @state/electron-main-db :environment :latest-release)]
    (str (:version_major rel)
         "." (:version_minor rel)
         "." (:version_patch rel)
         (when-let [pre (:version_pre rel)] (str "-" pre))
         (when-let [build (:version_build rel)] (str "+" build)))))

(defn version-and-os-component []
  (let [version (or (full-version) @release/version*)
        url (when version
              (str "https://github.com/Madek/madek-exporter/releases#release-" version))]
    [:div.version {:id "help-version"}
     [:h4 (i18n/t :help/version-and-os)]
     [:p (i18n/t :help/version-prefix) [:code version]]
     [:p (i18n/t :help/date-of-creation) [:code "2026-08-07"]]
     (when url
       [:p (i18n/t :help/release) [external-link url]])
     [:h5 (i18n/t :help/supported-os)]
     [:ul
      [:li "macOS (x64, ARM64)"]
      [:li "Linux (x64, ARM64)"]
      [:li "Windows (x64)"]]]))

(defn login-variants-component []
  [:div.login-variants {:id "help-login-variants"}
   [:h4 (i18n/t :help/login-variants)]
   [:h5 (i18n/t :help/sign-in-token)]
   [:ul
    [:li [:code "api-token"] " only"]]
   [:h5 (i18n/t :help/sign-in-login)]
   [:ul
    [:li [:code "login/api-token"]]
    [:li [:code "email/api-token"]]
    [:li [:code "api-client/pw"]]
    [:li [:code "login/pw"] (i18n/t :help/login-underscore) [:code "_"] (i18n/t :help/login-underscore-end)]]
   [:p (i18n/t :help/login-help)]])

;;; prefix examples ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:private example-set-uuid "3f1c9a2e-8d4b-4c6a-9e2f-1a7b5c0d9e21")
(def ^:private example-entry-uuid "b72e04d1-5c3a-4f8e-a1d9-6e0c2b8f4a73")

(def ^:private image-width 640)
(def ^:private tree-top 96)
(def ^:private row-height 22)

(defn- folder-icon []
  [:path {:d "M0 3a1 1 0 0 1 1-1h5l2 2h7a1 1 0 0 1 1 1v8a1 1 0 0 1-1 1H1a1 1 0 0 1-1-1z"
          :fill "#6ea6dd"}])

(defn- file-icon []
  [:path {:d "M2.5 0.5h7l4 4v9h-11z"
          :fill "#fff"
          :stroke "#999"}])

(defn- select-mock [x label value]
  [:g {:transform (str "translate(" x ",0)")}
   [:text {:x 0 :y 20 :font-size 13 :font-weight "bold" :fill "#333"} label]
   [:rect {:x 0.5 :y 30.5 :width 292 :height 30 :rx 4
           :fill "#fff" :stroke "#ccc"}]
   [:text {:x 10 :y 50 :font-size 13 :fill "#555"} value]
   [:path {:d "M272 43l5 5l5-5" :fill "none" :stroke "#555" :stroke-width 1.5}]])

(defn- tree-row [i [depth kind prefix suffix]]
  [:g {:transform (str "translate(" (+ 16 (* depth 20)) ","
                       (+ tree-top (* i row-height)) ")")}
   (if (= kind :folder) [folder-icon] [file-icon])
   [:text {:x 22 :y 12 :font-size 12
           :font-family "Menlo, Monaco, Consolas, monospace"
           :fill "#333"}
    (when prefix
      [:tspan {:fill "#337ab7" :font-weight "bold"} prefix])
    suffix]])

(defn- example-tree [set-prefix entry-prefix]
  [[0 :folder nil "~/export"]
   [1 :file nil "meta-data_schema.json"]
   [1 :folder set-prefix example-set-uuid]
   [2 :file nil "index.html"]
   [2 :file nil "meta-data.json"]
   [2 :file set-prefix (str example-set-uuid "_meta-data.json")]
   [2 :folder entry-prefix example-entry-uuid]
   [3 :file nil "index.html"]
   [3 :file nil "meta-data.json"]
   [3 :file entry-prefix (str example-entry-uuid "_meta-data.json")]
   [3 :folder nil "media-files"]])

(defn- prefix-example-image [meta-key-label rows]
  (let [height (+ tree-top (* (count rows) row-height))]
    [:svg.prefix-example-image
     {:xmlns "http://www.w3.org/2000/svg"
      :viewBox (str "0 0 " image-width " " height)
      :role "img"
      :aria-label meta-key-label}
     [:rect {:x 0 :y 0 :width image-width :height height :fill "#fafafa"}]
     [:g {:transform "translate(16,0)"}
      [select-mock 0 (i18n/t :download/vocabulary) "Madek Core"]
      [select-mock 316 (i18n/t :download/meta-key) meta-key-label]]
     [:line {:x1 16 :y1 78.5 :x2 (- image-width 16) :y2 78.5
             :stroke "#ddd" :stroke-dasharray "4 3"}]
     (map-indexed (fn [i row] ^{:key i} [tree-row i row]) rows)]))

(defn prefix-examples-component []
  (let [title (i18n/t :help/prefix-example-title)
        none (i18n/t :download/no-meta-key-prefix)]
    [:div.prefix-examples {:id prefix-examples-id}
     [:h4 (i18n/t :help/prefix-examples)]
     [:p (i18n/t :help/prefix-examples-intro)]
     [:h5 (i18n/t :help/prefix-example-with {:title title})]
     [prefix-example-image title (example-tree "Diplom 2026_" "Poster_")]
     [:p.help-block (i18n/t :help/prefix-example-with-help)]
     [:h5 (i18n/t :help/prefix-example-without {:option none})]
     [prefix-example-image none (example-tree nil nil)]
     [:p.help-block (i18n/t :help/prefix-example-without-help)]]))

(defn references-component []
  [:div.references {:id "help-references"}
   [:h4 (i18n/t :help/references)]
   [:ul
    [:li [external-link "https://zhdk.medienarchiv.ch/"]]
    [:li [external-link "https://wiki.zhdk.ch/medienarchiv/doku.php?id=madek-exporter"]]
    [:li [external-link "https://zhdk.medienarchiv.ch/api/browser/"]]
    [:li [external-link "https://github.com/Madek/madek-exporter/releases"]]]])

(defn kontakt-component []
  [:div.kontakt {:id "help-contact"}
   [:h4 [external-link "https://www.zhdk.ch/miz/archive-1387/madek/kontakt-1874"
                        (i18n/t :help/contact)]]
   [:p (i18n/t :help/email) [external-link "mailto:support.medienarchiv@zhdk.ch"
                                           "support.medienarchiv@zhdk.ch"]]])

(def ^:private toc-sections
  [["help-version" :help/version-and-os version-and-os-component]
   ["help-login-variants" :help/login-variants login-variants-component]
   [prefix-examples-id :help/prefix-examples prefix-examples-component]
   ["help-references" :help/references references-component]
   ["help-contact" :help/contact kontakt-component]])

(defn- section-component [id]
  (or (some (fn [[section-id _ component]]
              (when (= section-id id) component))
            toc-sections)
      version-and-os-component))

(defn toc-component []
  (let [collapsed? @toc-collapsed*
        selected @selected-section*
        toggle-label (i18n/t (if collapsed? :help/toc-expand :help/toc-collapse))]
    [:nav.toc {:class (when collapsed? "collapsed")}
     [:div.toc-header
      [:button.btn.btn-default.btn-xs.toc-toggle
       {:type "button"
        :title toggle-label
        :aria-expanded (not collapsed?)
        :aria-controls "help-toc-list"
        :on-click #(swap! toc-collapsed* not)}
       [:span.glyphicon
        {:class (if collapsed?
                  "glyphicon-chevron-right"
                  "glyphicon-chevron-left")
         :aria-hidden "true"}]
       [:span.sr-only toggle-label]]
      (when-not collapsed?
        [:h4 (i18n/t :help/toc)])]
     (when-not collapsed?
       [:ul#help-toc-list.nav.nav-pills.nav-stacked
        (for [[id label-key] toc-sections]
          ^{:key id}
          [:li {:class (when (= id selected) "active")}
           [:a {:href (str "#" id)
                :on-click (fn [e]
                            (.preventDefault e)
                            (reset! selected-section* id))}
            (i18n/t label-key)]])])]]))

(defn page []
  (consume-requested-section!)
  (let [selected @selected-section*]
    [:div.page.help
     [breadcrumb/page-breadcrumb (i18n/t :help/title)]
     [:div.help-layout {:class (when @toc-collapsed* "toc-collapsed")}
      [toc-component]
      [:div.help-content
       [(section-component selected)]]]]))
