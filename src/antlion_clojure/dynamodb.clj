(ns antlion-clojure.dynamodb
  (:import (com.amazonaws ClientConfiguration))
  (:require [clojure.set :refer [difference intersection]]
            [com.stuartsierra.component :as component]
            [taoensso.faraday :as far]))

(def tables
  {:antlion-clojure-user [[:id :s]
                          {:throughput {:read 1 :write 1}
                           :block? true}]
   :antlion-clojure-problem [[:question :s]
                             {:throughput {:read 1 :write 1}
                              :block? true}]
   :antlion-clojure-channel [[:id :s]
                             {:throughput {:read 1 :write 1}
                              :block? true}]
   :antlion-clojure-fyi [[:title :s]
                         {:throughput {:read 1 :write 1}
                          :block? true}]
   :antlion-clojure-notify-channel [[:name :s]
                         {:throughput {:read 1 :write 1}
                          :block? true}]
   :antlion-clojure-last-state [[:name :s]
                         {:throughput {:read 1 :write 1}
                          :block? true}]})

(defn delete-tables
  [{:keys [opts] :as comp}]
  (doseq [t (intersection (-> tables keys set) (-> opts far/list-tables set))]
    (far/delete-table opts t)))

(defn provision-tables
  [{:keys [opts] :as comp}]
  (let [exists-tables (set (far/list-tables opts))]
    (doseq [[k v] tables]
      (if (k exists-tables)
        (apply far/update-table (conj [opts k] (second v)))
        (apply far/create-table (concat [opts k] v))))))

(defn get-leaving-allowed?
  [{:keys [opts] :as comp} user-id]
  (:leaving-allowed? (far/get-item opts :antlion-clojure-user {:id user-id})))

(defn set-leaving-allowed?
  [{:keys [opts] :as comp} user-id]
  (far/update-item opts :antlion-clojure-user {:id user-id} {:leaving-allowed? [:put true]}))

(defn rm-leaving-allowed?
  [{:keys [opts] :as comp} user-id]
  (far/update-item opts :antlion-clojure-user {:id user-id} {:leaving-allowed? [:put false]}))

(defn get-checking-question?
  [{:keys [opts] :as comp} user-id]
  (:checking-question? (far/get-item opts :antlion-clojure-user {:id user-id})))

(defn set-checking-question?
  [{:keys [opts] :as comp} user-id question]
  (far/update-item opts :antlion-clojure-user {:id user-id} {:checking-question? [:put question]}))

(defn rm-checking-question?
  [{:keys [opts] :as comp} user-id]
  (far/update-item opts :antlion-clojure-user {:id user-id} {:checking-question? [:delete]}))

(defn get-problem
  [{:keys [opts] :as comp} question]
  (far/get-item opts :antlion-clojure-problem {:question question}))

(defn get-all-problem
  [{:keys [opts] :as comp}]
  (far/scan opts :antlion-clojure-problem))

(defn set-problem
  [{:keys [opts] :as comp} question answer]
  (far/update-item opts :antlion-clojure-problem
                   {:question question}
                   {:answer [:put (far/freeze answer)]}))

(defn rm-problem
  [{:keys [opts] :as comp} question]
  (far/delete-item opts :antlion-clojure-problem {:question question}))

(defn get-all-fyi
  [{:keys [opts] :as comp} user-id]
  (far/scan opts :antlion-clojure-fyi {:attr-conds {:user-id [:eq user-id]}}))

(defn set-fyi
  [{:keys [opts] :as comp} user-id title information]
  (far/update-item opts :antlion-clojure-fyi
                   {:title title}
                   {:information [:put information]
                    :user-id [:put user-id]}))

(defn rm-fyi
  [{:keys [opts] :as comp} user-id title]
  (far/delete-item opts :antlion-clojure-fyi {:title title}))

(defn get-all-leaving-allowed-channels
  [{:keys [opts] :as comp}]
  (map :id (far/scan opts :antlion-clojure-channel {:attr-conds {:leaving-allowed? [:eq true]}})))

(defn add-leaving-allowed-channel
  [{:keys [opts] :as comp} id]
  (far/update-item opts :antlion-clojure-channel
                   {:id id}
                   {:leaving-allowed? [:put true]}))

(defn rm-leaving-allowed-channel
  [{:keys [opts] :as comp} id]
  (far/update-item opts :antlion-clojure-channel
                   {:id id}
                   {:leaving-allowed? [:put false]}))

(defn get-all-reviewers
  [{:keys [opts] :as comp}]
  (far/scan opts :antlion-clojure-user {:attr-conds {:reviewer? [:eq true]}}))

(defn add-reviewer
  [{:keys [opts] :as comp} github-id id]
  (far/update-item opts :antlion-clojure-user
                   {:id id}
                   {:reviewer? [:put true]
                    :github-id [:put github-id]}))

(defn rm-reviewer
  [{:keys [opts] :as comp} id]
  (far/update-item opts :antlion-clojure-user
                   {:id id}
                   {:reviewer? [:put false]}))

(defn- get-notify
  [{:keys [opts] :as comp} name]
  (far/get-item opts :antlion-clojure-notify-channel {:name name}))

(defn get-lemming-channel
  [{:keys [opts] :as comp}]
  (get-notify comp "lemming"))

(defn- set-notify
  [{:keys [opts] :as comp} name channel-id]
  (far/update-item opts :antlion-clojure-notify-channel
                   {:name name}
                   {:channel-id [:put channel-id]}))

(defn set-lemming-channel
  [{:keys [opts] :as comp} channel-id]
  (set-notify comp "lemming" channel-id))

(defn- get-last-state
  [{:keys [opts] :as comp} name]
  (far/get-item opts :antlion-clojure-last-state {:name name}))

(defn get-lemming-last-state
  [{:keys [opts] :as comp}]
  (get-last-state comp "lemming"))

(defn- set-last-state
  [{:keys [opts] :as comp} name state]
  (far/put-item opts :antlion-clojure-last-state
                {:name name}
                {:state state}))

(defn set-lemming-last-state
  [{:keys [opts] :as comp} state]
  (set-last-state comp "lemming" state))

(defrecord DynamoDBComponent [opts access-key secret-key endpoint]
  component/Lifecycle
  (start [{:keys [access-key secret-key endpoint] :as this}]
    (println ";; Starting DynamoDBComponent")
    (let [opts {:access-key access-key
                :secret-key secret-key
                :endpoint endpoint}
          this (assoc this :opts opts)]
      (provision-tables this)
      this))
  (stop [this]
    (println ";; Stopping DynamoDBComponent")
    (dissoc this :opts)))

(defn dynamodb-component
  [access-key secret-key endpoint]
  (->DynamoDBComponent {} access-key secret-key endpoint))
