(ns antlion-clojure.infra.repository.lemming
  (:require [com.stuartsierra.component :as component]
            [clojure.core.async :refer [go put! <! close! chan]]
            [cheshire.core :refer [parse-string]]
            [antlion-clojure.infra.datasource.pubsub :refer [create-subscription add-subscriber]]
            [antlion-clojure.domain.entity.co2 :refer [map->Co2]]))

(def topic-key :lemming)
(def subscription-key :lemming-antlion-clojure)

(defn message->Co2
  [message]
  (-> message .getData .toStringUtf8 (parse-string true) map->Co2))

(defn subscribe
  [comp]
  (:channel comp))

(defrecord LemmingRepositoryComponent [pubsub-subscription channel]
  component/Lifecycle
  (start [this]
    (let [c (chan)]
      (println ";; Starting LemmingRepositoryComponent")
      (try
        (create-subscription (:pubsub-subscription this) topic-key subscription-key)
        (catch Exception e
          (println "Warning: Already" topic-key "has exists")))
      (-> this
          (update :pubsub-subscription
                  #(add-subscriber % topic-key subscription-key
                                   (fn [m]
                                     (put! c (message->Co2 m)))))
          (assoc :channel c))))
  (stop [this]
    (println ";; Stopping LemmingRepositoryComponent")
    (close! (:channel this))
    (-> this
        (dissoc :channel))))

(defn lemming-repository-component
  []
  (map->LemmingRepositoryComponent {}))
