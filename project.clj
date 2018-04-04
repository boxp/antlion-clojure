(defproject antlion-clojure "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/core.async "0.4.474"]
                 [org.julienxx/clj-slack "0.5.5"]
                 [slack-rtm "0.1.7"]
                 [clojail "1.0.6"]
                 [org.clojure/tools.namespace "0.2.10"]
                 [com.taoensso/carmine "2.15.0"]
                 [environ "1.1.0"]
                 [ring "1.6.2"]
                 [ring/ring-json "0.4.0"]
                 [compojure "1.6.0"]
                 [cheshire "5.7.1"]
                 [tentacles "0.5.1"]
                 [com.stuartsierra/component "0.3.2"]
                 [com.taoensso/faraday "1.9.0"]
                 [com.google.cloud/google-cloud-pubsub "0.17.2-alpha"]]
  :profiles
  {:dev {:source-paths ["src" "dev"]}
   :uberjar {:main antlion-clojure.system}})
