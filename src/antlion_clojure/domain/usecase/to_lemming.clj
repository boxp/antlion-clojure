(ns antlion-clojure.domain.usecase.to-lemming
  (:require [cheshire.core :refer [generate-string]]
            [com.stuartsierra.component :as component]
            [antlion-clojure.infra.repository.to-lemming :as r]
            [antlion-clojure.domain.entity.operation :refer [map->Led map->Operation]]))

(defn set-led
  [{:keys [to-lemming-repository] :as comp} value]
  (->> (map->Operation
         {:led (map->Led {:value value})})
       generate-string
       (r/publish-message to-lemming-repository)))

(defrecord ToLemmingUseCaseComponent [to-lemming-repository]
  component/Lifecycle
  (start [this]
    (println ";; Starting ToLemmingUseCaseComponent")
    this)
  (stop [this]
    (println ";; Stopping ToLemmingUseCaseComponent")
    this))

(defn to-lemming-usecase-component []
  (map->ToLemmingUseCaseComponent {}))
