(defproject antlion-clojure "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/core.async "0.2.395"]
                 [org.julienxx/clj-slack "0.5.4"]
                 [slack-rtm "0.1.6"]
                 [clojail "1.0.6"]
                 [org.clojure/tools.namespace "0.2.10"]
                 [com.taoensso/carmine "2.15.0"]
                 [environ "1.1.0"]
                 [http-kit "2.2.0"]
                 [tentacles "0.5.1"]
                 [com.stuartsierra/component "0.3.2"]
                 [com.taoensso/faraday "1.8.0"]]
  :profiles
  {:dev {:source-paths ["src" "dev"]}
   :uberjar {:main antlion-clojure.system}})
