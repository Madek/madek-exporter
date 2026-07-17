(defproject madek "0.4.0"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "GPL"
            :url "https://de.wikipedia.org/wiki/GNU_General_Public_License"}

  ;; Pin transitive versions that OSV flags.
  :managed-dependencies [[com.fasterxml.jackson.core/jackson-core "2.18.9"]
                         [com.fasterxml.jackson.core/jackson-databind "2.18.9"]
                         [com.fasterxml.jackson.core/jackson-annotations "2.18.9"]
                         [com.fasterxml.jackson.dataformat/jackson-dataformat-cbor "2.18.9"]
                         [com.fasterxml.jackson.dataformat/jackson-dataformat-smile "2.18.9"]
                         [commons-fileupload "1.5"]
                         [org.apache.commons/commons-fileupload2-core "2.0.0-M5"]
                         [commons-io "2.15.1"]
                         [org.apache.httpcomponents/httpclient "4.5.14"]
                         [org.apache.httpcomponents/httpcore "4.4.16"]
                         [org.apache.httpcomponents/httpmime "4.5.14"]
                         [com.google.code.gson/gson "2.11.0"]
                         [com.google.protobuf/protobuf-java "3.25.5"]
                         [com.google.guava/guava "32.1.3-jre"]
                         [org.yaml/snakeyaml "2.3"]
                         ;; 1.15.11+ data_readers.cljc uses #? conditionals; breaks cljs 1.10.773
                         [org.flatland/ordered "1.15.10"]]

  :dependencies [

                 [camel-snake-kebab "0.4.0"]
                 [com.taoensso/sente "1.11.0"]
                 [compojure "1.6.0"]
                 [clj-commons/clj-yaml "1.0.29"]
                 ;; direct pin: cljsbuild subproject ignores :managed-dependencies;
                 ;; ordered 1.15.11+ data_readers.cljc (#?) breaks cljs 1.10.773
                 [org.flatland/ordered "1.15.10"]
                 [clj-time "0.15.2"]
                 [funcool/cuerdas "2022.06.16-403"]
                 [environ "1.1.0"]
                 [fipp "0.6.9"]
                 [hiccup "1.0.5"]
                 [http-kit "2.8.1"]
                 [inflections "0.13.0"]
                 ;; drop log4j + unused ring umbrella (Jetty) pulled via uritemplate-clj
                 [json-roa_clj-client "0.2.1" :exclusions [org.slf4j/slf4j-log4j12
                                                          log4j/log4j
                                                          ring]]
                 [ch.qos.reload4j/reload4j "1.2.26"]
                 [logbug "4.2.2" :exclusions [log4j/log4j]]
                 [org.apache.commons/commons-lang3 "3.18.0"]
                 [org.clojure/clojure "1.11.4"]
                 [org.clojure/core.async "0.3.442"]
                 [org.clojure/data.codec "0.1.0"]
                 [org.clojure/data.json "2.5.1"]
                 [org.clojure/tools.reader "1.3.6"]
                 [org.slf4j/slf4j-reload4j "1.7.36"]
                 ;; ring modules only (no ring-jetty-adapter / Jetty)
                 [ring/ring-core "1.11.0"]
                 [ring/ring-defaults "0.4.0"]
                 [ring/ring-json "0.5.1"]
                 [timothypratley/patchin "0.3.5"]
                 [nrepl "1.0.0"]

                 ]

  ; jdk 9 needs ["--add-modules" "java.xml.bind"]
  :jvm-opts #=(eval (if (re-matches #"^(9|10)\..*" (System/getProperty "java.version"))
                      ["--add-modules" "java.xml.bind"]
                      []))

  :plugins [
            [lein-cljsbuild "1.1.5"]
            [lein-environ "1.0.2"]
            [lein-externs "0.1.6"]
            [lein-libdir "0.1.1"]
            [lein-shell "0.5.0"]
            ]

  :source-paths ["jvm_main/src"]

  :profiles {:dev {:dependencies [;; Electron cljs front/main deps — not shipped in jvm uberjar
                                  [cljs-http "0.1.43"]
                                  [cljsjs/moment "2.17.1-0"]
                                  [com.lucasbradstreet/cljs-uuid-utils "1.0.2"]
                                  [org.clojure/clojurescript "1.10.773" :exclusions [org.apache.ant/ant]]
                                  ;; pin for cljs/closure-compiler; bump together with cljs
                                  [com.google.guava/guava "21.0"]
                                  [reagent "0.6.1"]
                                  [secretary "1.2.3"]
                                  [venantius/accountant "0.2.0" :exclusions [org.clojure/tools.reader]]
                                  [figwheel "0.5.10"]]
                   :env {:dev true}
                   :plugins [[lein-figwheel "0.5.10" :exclusions [org.clojure/core.cache]]
                             [lein-sassy "1.0.7"]]
                   :repl-options {:init-ns madek.exporter.main}
                   :source-paths ["jvm_main/src", "electron_front/src/dev"]
                   :resource-paths["jvm_main/resources/dev"]
                   }

             :prod {}

             :uberjar {
                       :prep-tasks ["compile"]
                       :source-paths ["jvm_main/src"]
                       :resource-paths["jvm_main/resources/prod"]
                       :env {:production true}
                       :uberjar-name "../app/prod/jvm-main.jar"
                       :aot [madek.exporter.main]
                       :jar true
                       :main madek.exporter.main
                       }
             }
  :aliases {"descjop-help" ["new" "descjop" "help"]
            "descjop-version" ["new" "descjop" "version"]
            "descjop-init" ["shell" "npm" "install"]
            "descjop-init-win" ["shell" "cmd.exe" "/c" "npm" "install"]
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
            "descjop-uberapp-osx" ["shell" "./bin/build-mac-os"]
            "descjop-uberapp-linux" ["shell" "./bin/build-linux"]
            "descjop-uberapp-win64" ["shell" "./bin/build-win"]
            "descjop-uberapp-app-store" ["shell" "echo" "Not implemented; use @electron/packager manually if needed."]
            "descjop-uberapp-win32" ["shell" "echo" "Not implemented; win32 ia32 package is not part of the current pipeline."]
            }
;:hooks [leiningen.cljsbuild]

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
             :uberjar {
                       :source-paths ["jvm_main/src"]
                       :jar false
                       :compiler {}
                       }
             }}
:figwheel {:http-server-root "public"
           :ring-handler madek.app.front.figwheel-middleware/app
           :server-port 8384})
