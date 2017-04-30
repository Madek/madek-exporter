(defproject madek "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [

                 [cljs-http "0.1.39"]
                 [cljsjs/moment "2.10.6-3"]
                 [com.lucasbradstreet/cljs-uuid-utils "1.0.2"]
                 [com.taoensso/sente "1.8.1"]
                 [compojure "1.5.2"]
                 [environ "1.0.2"]
                 [figwheel "0.5.9"]
                 [fipp "0.6.8"]
                 [hiccup "1.0.5"]
                 [json-roa_clj-client "0.2.0"]
                 [logbug "4.2.2"]
                 [org.apache.commons/commons-lang3 "3.4"]
                 [org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.9.473" :exclusions [org.apache.ant/ant]]
                 [org.clojure/core.async "0.2.395"]
                 [org.immutant/web "2.1.2" :exclusions [ch.qos.logback/logback-classic]]
                 [reagent "0.6.0"]
                 [ring "1.4.0"]
                 [ring-undertow-adapter "0.2.2"]
                 [ring/ring-core "1.5.1"]
                 [ring/ring-defaults "0.1.5"]
                 [ring/ring-json "0.4.0"]
                 [secretary "1.2.3"]
                 [timothypratley/patchin "0.3.5"]
                 [venantius/accountant "0.1.6" :exclusions [org.clojure/tools.reader]]

                 ; DON'T REMOVE THIS the clojurescript stuff depends on this version
                 ; check and upgrade it when updating clojurescript
                 [com.google.guava/guava "20.0"]

                 ]
  :plugins [
            [lein-cljsbuild "1.1.5"]
            [lein-environ "1.0.2"]
            [lein-externs "0.1.6"]
            [lein-libdir "0.1.1"]
            [lein-shell "0.5.0"]
            [lein-figwheel "0.5.9" :exclusions [org.clojure/core.cache]]
            ]

  :source-paths ["src_jvm_main"]

  :profiles {:dev {;:dependencies [[figwheel "0.5.9"]]
                   :env {:dev true}
                   ;:plugins [ [lein-figwheel "0.5.9" :exclusions [org.clojure/core.cache]] ]
                   :repl-options {:init-ns madek.app.server.main}
                   :source-paths ["src_jvm_main", "src_front_profile/dev"]
                   }

             :uberjar {
                       :prep-tasks ["compile"]
                       :source-paths ["src_jvm_main"]
                       :env {:production true}
                       :uberjar-name "../app/prod/jvm-main.jar"
                       :aot [madek.app.server.main]
                       :jar true
                       :main madek.app.server.main
                       }
             }
  :aliases {"descjop-help" ["new" "descjop" "help"]
            "descjop-version" ["new" "descjop" "version"]
            "descjop-init" ["do"
                            ["shell" "npm" "install"]
                            ["shell" "node_modules/grunt/bin/grunt" "download-electron"]]
            "descjop-init-win" ["do"
                                ["shell" "cmd.exe" "/c" "npm" "install"]
                                ["shell" "cmd.exe" "/c" "grunt" "download-electron"]]
            "descjop-externs" ["do"
                               ["externs" "electron-main-dev" "app/dev/js/externs.js"]
                               ["externs" "electron-front-dev" "app/dev/js/externs_front.js"]
                               ["externs" "electron-main-prod" "app/prod/js/externs.js"]
                               ["externs" "electron-front-prod" "app/prod/js/externs_front.js"]]
            "descjop-externs-dev" ["do"
                                   ["externs" "electron-main-dev" "app/dev/js/externs.js"]
                                   ["externs" "electron-front-dev" "app/dev/js/externs_front.js"]]
            "descjop-externs-prod" ["do"
                                    ["externs" "electron-main-prod" "app/prod/js/externs.js"]
                                    ["externs" "electron-front-prod" "app/prod/js/externs_front.js"]]
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
            "descjop-uberapp-osx" ["shell" "electron-packager" "./app/prod" "madek-app" "--platform=darwin" "--arch=x64" "--electron-version=1.6.0"]
            "descjop-uberapp-app-store" ["shell" "cmd.exe" "/c" "electron-packager" "./app/prod" "madek-app" "--platform=mas" "--arch=x64" "--electron-version=1.6.0"]
            "descjop-uberapp-linux" ["shell" "cmd.exe" "/c" "electron-packager" "./app/prod" "madek-app" "--platform=linux" "--arch=x64" "--electron-version=1.6.0"]
            "descjop-uberapp-win64" ["shell" "electron-packager" "./app/prod" "madek-app" "--platform=win32" "--arch=x64" "--electron-version=1.6.0"]
            "descjop-uberapp-win32" ["shell" "cmd.exe" "/c" "electron-packager" "./app/prod" "madek-app" "--platform=win32" "--arch=ia32" "--electron-version=1.6.0"]
            }
;:hooks [leiningen.cljsbuild]

:cljsbuild {:builds
            {:electron-main-dev
             {:source-paths ["src_electron_main/all" "src_electron_main/dev"]
              :incremental true
              :jar true
              :assert true
              :compiler {:output-to "app/dev/js/cljsbuild-main.js"
                         :externs ["app/dev/js/externs.js"
                                   "node_modules/closurecompiler-externs/path.js"
                                   "node_modules/closurecompiler-externs/process.js"]
                         :warnings true
                         :elide-asserts true
                         :target :nodejs
                         :output-dir "app/dev/js/out_main"
                         :source-map "app/dev/js/source.map"

                         :optimizations :simple
                         :pretty-print true
                         :output-wrapper true}}
             :electron-main-prod
             {:source-paths ["src_electron_main/all" "src_electron_main/prod"]
              :incremental true
              :jar true
              :assert true
              :compiler {:output-to "app/prod/js/cljsbuild-main.js"
                         :externs ["app/prod/js/externs.js"
                                   "node_modules/closurecompiler-externs/path.js"
                                   "node_modules/closurecompiler-externs/process.js"]
                         :warnings true
                         :elide-asserts true
                         :target :nodejs

                         ;; no optimize compile (dev)
                         ;;:optimizations :none
                         :output-dir "app/prod/js/out_main"

                         ;; simple compile (dev)
                         :optimizations :simple

                         ;; advanced compile (prod)
                         ;;:optimizations :advanced

                         ;;:source-map "app/prod/js/test.js.map"
                         :pretty-print true
                         :output-wrapper true}}
             :electron-front-dev
             {:source-paths ["src_electron_front/all" "src_electron_front/dev"]
              :incremental true
              :jar true
              :assert true
              :compiler {:output-to "app/dev/js/front.js"
                         :externs ["app/dev/js/externs_front.js"]
                         :warnings true
                         :elide-asserts true
                         ;; :target :nodejs

                         ;; no optimize compile (dev)
                         :optimizations :none
                         :output-dir "app/dev/js/out_front"

                         ;; simple compile (dev)
                         ;;:optimizations :simple

                         ;; advanced compile (prod)
                         ;;:optimizations :advanced

                         ;;:source-map "app/dev/js/test.js.map"
                         :pretty-print true
                         :output-wrapper true}}
             :electron-front-prod
             {:source-paths ["src_electron_front/all" "src_electron_front/prod"]
              :incremental true
              :jar true
              :assert true
              :compiler {:output-to "app/prod/js/front.js"
                         :externs ["app/prod/js/externs_front.js"]
                         :warnings true
                         :elide-asserts true
                         ;; :target :nodejs

                         ;; no optimize compile (dev)
                         ;;:optimizations :none
                         :output-dir "app/prod/js/out_front"

                         ;; simple compile (dev)
                         ;;:optimizations :simple

                         ;; advanced compile (prod)
                         :optimizations :advanced

                         ;;:source-map "app/prod/js/test.js.map"
                         :pretty-print true
                         :output-wrapper true}}
             :uberjar {
                       :source-paths ["src_jvm_main"]
                       :jar false
                       :compiler {}
                       }
}}
:figwheel {:http-server-root "public"
           :ring-handler madek-front.figwheel-middleware/app
           :server-port 3449})
