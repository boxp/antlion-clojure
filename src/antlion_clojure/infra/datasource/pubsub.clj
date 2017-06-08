(ns antlion-clojure.infra.datasource.pubsub
  (:import (com.google.protobuf ByteString)
           (com.google.common.util.concurrent MoreExecutors)
           (com.google.api.core ApiService$Listener)
           (com.google.pubsub.v1 TopicName
                                 SubscriptionName
                                 PubsubMessage
                                 PushConfig)
           (com.google.cloud ServiceOptions)
           (com.google.cloud.pubsub.spi.v1 SubscriptionAdminClient
                                           Subscriber
                                           MessageReceiver))
  (:require [com.stuartsierra.component :as component]))

(defn create-subscription
  [comp topic-key subscription-key]
  (let [topic-name (TopicName/create (:project-id comp) (name topic-key))
        subscription-name (SubscriptionName/create (:project-id comp) (name subscription-key))
        push-config (-> (PushConfig/newBuilder) .build)
        ack-deadline-second 0]
    (-> (SubscriptionAdminClient/create)
        (.createSubscription subscription-name
                             topic-name
                             push-config
                             ack-deadline-second))))

(defn add-subscriber
  [comp topic-key subscription-key on-receive]
  (let [subscription-name (SubscriptionName/create (:project-id comp) (name subscription-key))
        receiver (reify MessageReceiver
                   (receiveMessage [this message consumer]
                     (on-receive message)
                     (.ack consumer)))
        listener (proxy [ApiService$Listener] []
                   (failed [from failure]))
        subscriber (-> (Subscriber/defaultBuilder subscription-name receiver) .build)]
    (.addListener subscriber listener (MoreExecutors/directExecutor))
    (-> subscriber .startAsync .awaitRunning)
    (-> comp
        (assoc-in [:subscribers subscription-key] subscriber))))

(defrecord PubSubSubscriptionComponent [project-id subscribers]
  component/Lifecycle
  (start [this]
    (println ";; Starting PubSubSubscriptionComponent")
    (-> this
        (assoc :project-id (ServiceOptions/getDefaultProjectId))))
  (stop [this]
    (println ";; Stopping PubSubSubscriptionComponent")
    (doall (map #(.stopAsync %) (:subscribers this)))
    (-> this
        (dissoc :project-id)
        (dissoc :subscribers))))

(defn pubsub-subscription-component
  []
  (map->PubSubSubscriptionComponent {}))
