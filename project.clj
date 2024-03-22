(defproject madek "0.9.8"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "GPL"
            :url "https://de.wikipedia.org/wiki/GNU_General_Public_License"}
  :dependencies [; enforce higher encore version: current timbre needs an newer
                 ; encore than sente
                 [com.taoensso/encore "3.79.0"]

                 [camel-snake-kebab "0.4.0"]
                 [cljs-http "0.1.43"]
                 [cljsjs/moment "2.17.1-0"]
                 [com.lucasbradstreet/cljs-uuid-utils "1.0.2"]
                 [com.taoensso/sente "1.11.0"]
                 [compojure "1.6.0"]
                 [environ "1.1.0"]
                 [fipp "0.6.9"]
                 [hiccup "1.0.5"]
                 [inflections "0.13.0"]
                 [json-roa_clj-client "0.2.1"]
                 [logbug "4.2.2"]
                 [org.apache.commons/commons-lang3 "3.5"]
                 [org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.9.671" :exclusions [org.apache.ant/ant]]
                 [org.clojure/core.async "0.3.442"]
                 [org.clojure/data.codec "0.1.0"]
                 [org.immutant/web "2.1.6" :exclusions [ch.qos.logback/logback-classic]]
                 [org.slf4j/slf4j-log4j12 "1.7.25"]
                 [reagent "0.6.1"]
                 [ring "1.6.0"]
                 [ring-undertow-adapter "0.2.2"]
                 [ring/ring-core "1.6.0"]
                 [ring/ring-defaults "0.3.0"]
                 [ring/ring-json "0.4.0"]
                 [secretary "1.2.3"]
                 [timothypratley/patchin "0.3.5"]
                 [com.taoensso/timbre "6.3.1"]

                 ; added for nrepl
                 [funcool/cuerdas "2023.11.09-407"]
                 [nrepl "1.1.1"]
                 [clj-yaml "0.4.0"]
                 ;[clj-commons/clj-yaml "1.0.27"]

                 [venantius/accountant "0.2.0" :exclusions [org.clojure/tools.reader]]
                 ; DON'T REMOVE THIS the clojurescript stuff depends on this version
                 ; check and upgrade it when updating clojurescript
                 [com.google.guava/guava "21.0"]

                 [javax.xml.bind/jaxb-api "2.3.1"]
                 ]

  ;jdk since 9 needs ["--add-modules" "java.xml.bind"]
  ;:jvm-opts ["--add-modules" "java.xml.bind"]

  :plugins [[lein-cljfmt "0.9.2"]
            [lein-cljsbuild "1.1.5"]
            [lein-environ "1.0.2"]
            [lein-externs "0.1.6"]
            [lein-libdir "0.1.1"]
            [lein-shell "0.5.0"]]

  :source-paths ["jvm_main/src"]

  :profiles {:dev {:dependencies [[figwheel "0.5.10"]]
                   :env {:dev true}
                   :plugins [[lein-figwheel "0.5.10" :exclusions [org.clojure/core.cache]]
                             [lein-sassy "1.0.7"]]
                   :repl-options {:init-ns madek.exporter.main}
                   :main madek.exporter.main
                   :source-paths ["jvm_main/src", "electron_front/src/dev"]
                   :resource-paths ["jvm_main/resources/dev"]}

             :prod {}

             :uberjar {:prep-tasks ["compile"]
                       :source-paths ["jvm_main/src"]
                       :resource-paths ["jvm_main/resources/prod"]
                       :env {:production true}
                       :uberjar-name "../app/prod/jvm-main.jar"
                       :aot [madek.exporter.main]
                       :jar true
                       :main madek.exporter.main}}

  :aliases {"descjop-help" ["new" "descjop" "help"]
            "descjop-version" ["new" "descjop" "version"]
            "descjop-init" ["do"
                            ["shell" "npm" "install"]
                            ["shell" "node_modules/grunt/bin/grunt" "download-electron"]]
            "descjop-init-win" ["do"
                                ["shell" "cmd.exe" "/c" "npm" "install"]
                                ["shell" "cmd.exe" "/c" "grunt" "download-electron"]]
            "descjop-figwheel" ["trampoline" "figwheel" "electron-front-dev"]
            "descjop-once" ["do"
                            ["cljsbuild" "once" "electron-main-dev"]
                            ["cljsbuild" "once" "electron-front-dev"]
                            ["cljsbuild" "once" "electron-main-prod"]
                            ["cljsbuild" "once" "electron-front-prod"]]
            "descjop-once-dev" ["do"
                                ["cljsbuild" "once" "electron-main-dev"]
                                ["cljsbuild" "once" "electron-front-dev"]]
            "descjop-once-prod" ["do"
                                 ["cljsbuild" "once" "electron-main-prod"]
                                 ["cljsbuild" "once" "electron-front-prod"]]
            ;; electron packager for production
            "descjop-uberapp-osx" ["shell" "electron-packager" "./app/prod" "madek-exporter" "--platform=darwin" "--arch=x64" "--electron-version=1.6.0"]
            "descjop-uberapp-app-store" ["shell" "cmd.exe" "/c" "electron-packager" "./app/prod" "madek-exporter" "--platform=mas" "--arch=x64" "--electron-version=1.6.0"]
            "descjop-uberapp-linux" ["shell" "cmd.exe" "/c" "electron-packager" "./app/prod" "madek-exporter" "--platform=linux" "--arch=x64" "--electron-version=1.6.0"]
            "descjop-uberapp-win64" ["shell" "electron-packager" "./app/prod" "madek-exporter" "--platform=win32" "--arch=x64" "--electron-version=1.6.0"]
            "descjop-uberapp-win32" ["shell" "cmd.exe" "/c" "electron-packager" "./app/prod" "madek-exporter" "--platform=win32" "--arch=ia32" "--electron-version=1.6.0"]}
;:hooks [leiningen.cljsbuild]

  :cljfmt {:remove-multiple-non-indenting-spaces? true
           :sort-ns-references? true}

  :cljsbuild {:builds
              {:electron-main-dev
               {:source-paths ["electron_main/src/all" "electron_main/src/dev"]
                :incremental true
                :jar true
                :assert true
                :compiler {:output-to "app/dev/js/main.js"
                           :externs ["app/dev/js/main_externs.js"
                                     "node_modules/closurecompiler-externs/path.js"
                                     "node_modules/closurecompiler-externs/process.js"
                                     "js-yaml_externs.js"]
                           :warnings true
                           :elide-asserts true
                           :target :nodejs
                           :output-dir "app/dev/js/out_main"
                           :source-map true
                           :optimizations :none
                           :main "madek.app.main.main"
                           :pretty-print true
                           :output-wrapper true}}
               :electron-main-prod
               {:source-paths ["electron_main/src/all" "electron_main/src/prod"]
                :incremental true
                :jar true
                :assert true
                :compiler {:output-to "app/prod/js/main.js"
                           :externs ["app/prod/js/main_externs.js"
                                     "node_modules/closurecompiler-externs/path.js"
                                     "node_modules/closurecompiler-externs/process.js"]
                           :warnings true
                           :elide-asserts true
                           :target :nodejs
                           :output-dir "app/prod/js/out_main"
                           :optimizations :advanced
                           :source-map "app/prod/js/main.js.map"
                           :pretty-print true
                           :output-wrapper true}}
               :electron-front-dev
               {:source-paths ["electron_front/src/all" "electron_front/src/dev"]
                :incremental true
              ;:figwheel {:on-jsload madek.main/init!}
                :jar true
                :assert true
                :compiler {:output-to "app/dev/js/front.js"
                           :externs ["app/dev/js/front_externs.js"]
                           :warnings true
                           :elide-asserts true
                           :optimizations :none
                           :main "madek.app.front.init"
                           :output-dir "app/dev/js/out_front"
                           :asset-path "js/out_front"
                           :source-map true
                           :pretty-print true
                           :output-wrapper true}}
               :electron-front-prod
               {:source-paths ["electron_front/src/all" "electron_front/src/prod"]
                :incremental true
                :jar true
                :assert true
                :compiler {:output-to "app/prod/js/front.js"
                           :externs ["app/prod/js/front_externs.js"]
                           :warnings true
                           :elide-asserts true
                           :output-dir "app/prod/js/out_front"
                           :optimizations :advanced
                           :source-map "app/prod/js/front.js.map"
                           :pretty-print true
                           :output-wrapper true}}
               :uberjar {:source-paths ["jvm_main/src"]
                         :jar false
                         :compiler {}}}}

  :figwheel {:http-server-root "public"
             :ring-handler madek.app.front.figwheel-middleware/app
             :server-port 8384})
