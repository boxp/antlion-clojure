(ns antlion-clojure.app.webapp.handler
  (:require [com.stuartsierra.component :as component]
            [cheshire.core :refer [generate-string]]))

(defn index
  [{:keys [] :as comp}]
  (-> {:message "hello"}
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
