(ns antlion-clojure.system
  (:require [com.stuartsierra.component :as component]
            [environ.core :refer [env]]
            [antlion-clojure.slack :refer [slack-component]]
            [antlion-clojure.dynamodb :refer [dynamodb-component]]
            [antlion-clojure.bot :refer [bot-component]]
            [antlion-clojure.app.webapp.handler :refer [webapp-handler-component]]
            [antlion-clojure.app.webapp.endpoint :refer [webapp-endpoint-component]]
            [antlion-clojure.infra.datasource.pubsub :refer [pubsub-subscription-component pubsub-publisher-component]]
            [antlion-clojure.infra.repository.lemming :refer [lemming-repository-component]]
            [antlion-clojure.infra.repository.to-lemming :refer [to-lemming-repository-component]]
            [antlion-clojure.domain.usecase.to-lemming :refer [to-lemming-usecase-component]]
            [antlion-clojure.infra.repository.page-speed :refer [->PageSpeedRepository]]
            [antlion-clojure.domain.usecase.page-speed :refer [->PageSpeedUsecase]])
  (:gen-class))

(defn antlion-clojure-system
  [{:keys [antlion-clojure-token
           antlion-clojure-invite-token
           antlion-clojure-aws-access-key
           antlion-clojure-aws-secret-key
           antlion-clojure-dynamodb-endpoint
           antlion-clojure-google-api-key
           master-user-name
           port]
    :as config-options}]
  (component/system-map
    :dynamodb (dynamodb-component antlion-clojure-aws-access-key antlion-clojure-aws-secret-key antlion-clojure-dynamodb-endpoint)
    :slack (slack-component antlion-clojure-token antlion-clojure-invite-token)
    :pubsub-subscription (pubsub-subscription-component)
    :pubsub-publisher (pubsub-publisher-component)
    :lemming-repository (component/using
                          (lemming-repository-component)
                          [:pubsub-subscription])
    :to-lemming-repository (component/using
                             (to-lemming-repository-component)
                             [:pubsub-publisher])
    :to-lemming-usecase (component/using
                             (to-lemming-usecase-component)
                             [:to-lemming-repository])
    :page-speed-repository (->PageSpeedRepository antlion-clojure-google-api-key)
    :page-speed-usecase (component/using
                          (->PageSpeedUsecase nil nil nil)
                          [:page-speed-repository :slack :dynamodb])
    :bot (component/using
           (bot-component {:master-user-name master-user-name})
           [:slack
            :dynamodb
            :lemming-repository
            :to-lemming-usecase
            :page-speed-usecase])
    :webapp-handler (webapp-handler-component)

    :webapp-endpoint (component/using
                       (webapp-endpoint-component port)
                       [:webapp-handler])))

(defn load-config []
  {:antlion-clojure-token (env :antlion-clojure-token)
   :antlion-clojure-invite-token (env :antlion-clojure-invite-token)
   :antlion-clojure-github-oauth-token (env :antlion-clojure-github-oauth-token)
   :antlion-clojure-aws-access-key (env :antlion-clojure-aws-access-key)
   :antlion-clojure-aws-secret-key (env :antlion-clojure-aws-secret-key)
   :antlion-clojure-dynamodb-endpoint (env :antlion-clojure-dynamodb-endpoint)
   :antlion-clojure-google-api-key (env :antlion-clojure-google-api-key)
   :master-user-name (env :antlion-clojure-master-user-name)
   :port (-> (or (env :port) "3000") Integer/parseInt)})

(defn -main []
  (component/start
    (antlion-clojure-system (load-config))))
