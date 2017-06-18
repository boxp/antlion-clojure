(ns antlion-clojure.infra.datasource.pubsub
  (:import (com.google.protobuf ByteString)
           (com.google.common.util.concurrent MoreExecutors)
           (com.google.api.core ApiFutures
                                ApiFutureCallback
                                ApiService$Listener)
           (com.google.cloud ServiceOptions)
           (com.google.cloud.pubsub.spi.v1 TopicAdminClient
                                           Publisher
                                           SubscriptionAdminClient
                                           Subscriber
                                           MessageReceiver)
           (com.google.pubsub.v1 TopicName
                                 SubscriptionName
                                 PubsubMessage
                                 PushConfig))
  (:require [com.stuartsierra.component :as component]))

(defn create-topic
  [comp topic-key]
  (let [topic-admin-cli (TopicAdminClient/create)]
    (try
        (->> (TopicName/create (:project-id comp)
                               (name topic-key))
             (.createTopic topic-admin-cli))
        (catch Exception e
          (TopicName/create (:project-id comp)
            (name topic-key))))))

(defn create-publisher
  [comp topic-key]
  (if-not (get (:publishers comp) topic-key)
    (let [topic-name (create-topic comp topic-key)]
          (->> topic-name
               Publisher/defaultBuilder
               .build
               (assoc-in comp [:publishers topic-key])))
    comp))

(defn publish
  [{:keys [publishers project-id] :as comp} topic-key message on-success on-failure]
  (let [data (ByteString/copyFromUtf8 message)
        pubsub-message (-> (PubsubMessage/newBuilder) (.setData data) .build)
        publisher (-> publishers topic-key)
        message-id-future (.publish publisher pubsub-message)
        callback (reify ApiFutureCallback
                   (onSuccess [this message-id]  #(on-success message-id))
                   (onFailure [this e] #(on-failure e)))]
    (ApiFutures/addCallback message-id-future callback)))

(defrecord PubSubPublisherComponent [project-id publishers]
  component/Lifecycle
  (start [this]
    (println ";; Starting PubSubPublisherComponent")
    (-> this
        (assoc :project-id (ServiceOptions/getDefaultProjectId))))
  (stop [this]
    (println ";; Stopping PubSubPublisherComponent")
    (-> this
        (dissoc :publishers)
        (dissoc :project-id))))

(defn pubsub-publisher-component
  []
  (map->PubSubPublisherComponent {}))

(defn create-subscription
  [comp topic-key subscription-key]
  (let [topic-name (create-topic comp topic-key)
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
