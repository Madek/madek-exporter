(defproject madek "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [

                 [cljs-http "0.1.43"]
                 [cljsjs/moment "2.17.1-0"]
                 [com.lucasbradstreet/cljs-uuid-utils "1.0.2"]
                 [com.taoensso/sente "1.8.1"]
                 [compojure "1.6.0"]
                 [environ "1.1.0"]
                 [fipp "0.6.9"]
                 [hiccup "1.0.5"]
                 [json-roa_clj-client "0.2.0"]
                 [log4j/log4j "1.2.17" :exclusions [javax.mail/mail javax.jms/jms com.sun.jdmk/jmxtools com.sun.jmx/jmxri]]
                 [logbug "4.2.2"]
                 [org.apache.commons/commons-lang3 "3.5"]
                 [org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.9.473" :exclusions [org.apache.ant/ant]]
                 [org.clojure/core.async "0.3.442"]
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
                 [venantius/accountant "0.2.0" :exclusions [org.clojure/tools.reader]]
                 ; DON'T REMOVE THIS the clojurescript stuff depends on this version
                 ; check and upgrade it when updating clojurescript
                 [com.google.guava/guava "21.0"]

                 ]
  :plugins [
            [lein-cljsbuild "1.1.5"]
            [lein-environ "1.0.2"]
            [lein-externs "0.1.6"]
            [lein-libdir "0.1.1"]
            [lein-shell "0.5.0"]
            ]

  :source-paths ["jvm_main/src"]

  :profiles {:dev {:dependencies [[figwheel "0.5.9"]]
                   :env {:dev true}
                   :plugins [[lein-figwheel "0.5.10" :exclusions [org.clojure/core.cache]]
                             [lein-sassy "1.0.7"]]
                   :repl-options {:init-ns madek.app.server.main}
                   :source-paths ["jvm_main/src", "electron_front/src/dev"]
                   :resource-paths["jvm_main/resources/dev"]
                   :sass {:src "electron_front/sass"
                          :dst "app/dev/css" }
                   }

             :prod {:sass {:src "electron_front/sass"
                           :dst "app/prod/css"
                           :style :compressed }
                    }
             :uberjar {
                       :prep-tasks ["compile"]
                       :source-paths ["jvm_main/src"]
                       :resource-paths["jvm_main/resources/prod"]
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
             {:source-paths ["electron_main/src/all" "electron_main/src/dev"]
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
                         :source-map true
                         :optimizations :none
                         :pretty-print true
                         :output-wrapper true}}
             :electron-main-prod
             {:source-paths ["electron_main/src/all" "electron_main/src/prod"]
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
                         :output-dir "app/prod/js/out_main"
                         :optimizations :advanced
                         :source-map "app/prod/js/cljsbuild-main.js.map"
                         :pretty-print true
                         :output-wrapper true}}
             :electron-front-dev
             {:source-paths ["electron_front/src/all" "electron_front/src/dev"]
              :incremental true
              ;:figwheel {:on-jsload madek.main/init!}
              :jar true
              :assert true
              :compiler {:output-to "app/dev/js/front.js"
                         :externs ["app/dev/js/externs_front.js"]
                         :warnings true
                         :elide-asserts true
                         :optimizations :none
                         :output-dir "app/dev/js/out_front"
                         :source-map true
                         :pretty-print true
                         :output-wrapper true}}
             :electron-front-prod
             {:source-paths ["electron_front/src/all" "electron_front/src/prod"]
              :incremental true
              :jar true
              :assert true
              :compiler {:output-to "app/prod/js/front.js"
                         :externs ["app/prod/js/externs_front.js"]
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
