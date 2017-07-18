(ns antlion-clojure.app.webapp.handler
  (:require [clojure.core.async :refer [go put! <! <!! close! chan]]
            [com.stuartsierra.component :as component]
            [compojure.core :refer [defroutes context GET POST routes]]
            [ring.adapter.jetty :as server]
            [cheshire.core :refer [generate-string]]))

(defn get-index
  [req]
  "Hi, I'm shanghai.")

(defn post-github-event
  [req]
  (println req)
  (-> {:message "OK"}
      generate-string))

(defrecord WebappHandlerComponent []
  component/Lifecycle
  (start [this]
    (println ";; Starting WebappHandlerComponent")
    this)
  (stop [this]
    (println ";; Stopping WebappHandlerComponent")
    this))

(defn webapp-handler-component
  []
  (map->WebappHandlerComponent {}))
