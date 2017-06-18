(ns antlion-clojure.infra.repository.to-lemming
  (:import (com.google.cloud ServiceOptions)
           (com.google.cloud.pubsub.spi.v1 TopicAdminClient)
           (com.google.pubsub.v1 TopicName))
  (:require [com.stuartsierra.component :as component]
            [antlion-clojure.infra.datasource.pubsub :refer [create-publisher publish]]
            [antlion-clojure.domain.entity.operation :refer [map->Led map->Operation]]))

(def topic-key :to-lemming)

(defn publish-message
  [{:keys [pubsub-publisher] :as comp} message]
  (publish pubsub-publisher topic-key message
           (fn [data])
           (fn [e] (println e))))

(defrecord ToLemmingRepositoryComponent [pubsub-publisher]
  component/Lifecycle
  (start [this]
    (println ";; Starting ToLemmingRepositoryComponent")
    (-> this
        (update :pubsub-publisher #(create-publisher % topic-key))))
  (stop [this]
    (println ";; Stopping ToLemmingRepositoryComponent")
    (-> this
        (dissoc :pubsub-publisher))))

(defn to-lemming-repository-component []
  (map->ToLemmingRepositoryComponent {}))
