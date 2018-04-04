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
                          :block? true}]
   :antlion-clojure-response [[:name :s]
                         {:throughput {:read 1 :write 1}
                          :block? true}]
   :antlion-clojure-stats [[:type :s]
                           {:throughput {:read 1 :write 1}
                            :block? true}]
   :antlion-clojure-schedule [[:name :s]
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
  (far/put-item opts :antlion-clojure-user {:id user-id
                                            :leaving-allowed? true}))

(defn rm-leaving-allowed?
  [{:keys [opts] :as comp} user-id]
  (far/put-item opts :antlion-clojure-user {:id user-id
                                            :leaving-allowed? false}))

(defn get-checking-question?
  [{:keys [opts] :as comp} user-id]
  (:checking-question? (far/get-item opts :antlion-clojure-user {:id user-id})))

(defn set-checking-question?
  [{:keys [opts] :as comp} user-id question]
  (far/put-item opts :antlion-clojure-user {:id user-id
                                            :checking-question? question}))

(defn rm-checking-question?
  [{:keys [opts] :as comp} user-id]
  (far/put-item opts :antlion-clojure-user {:id user-id
                                            :checking-question? nil}))

(defn get-problem
  [{:keys [opts] :as comp} question]
  (far/get-item opts :antlion-clojure-problem {:question question}))

(defn get-all-problem
  [{:keys [opts] :as comp}]
  (far/scan opts :antlion-clojure-problem))

(defn set-problem
  [{:keys [opts] :as comp} question answer]
  (far/put-item opts :antlion-clojure-problem
                   {:question question
                    :answer (far/freeze answer)}))

(defn rm-problem
  [{:keys [opts] :as comp} question]
  (far/delete-item opts :antlion-clojure-problem {:question question}))

(defn get-all-fyi
  [{:keys [opts] :as comp} user-id]
  (far/scan opts :antlion-clojure-fyi {:attr-conds {:user-id [:eq user-id]}}))

(defn set-fyi
  [{:keys [opts] :as comp} user-id title information]
  (far/put-item opts :antlion-clojure-fyi
                   {:title title
                    :information information
                    :user-id user-id}))

(defn rm-fyi
  [{:keys [opts] :as comp} user-id title]
  (far/delete-item opts :antlion-clojure-fyi {:title title}))

(defn get-all-leaving-allowed-channels
  [{:keys [opts] :as comp}]
  (map :id (far/scan opts :antlion-clojure-channel {:attr-conds {:leaving-allowed? [:eq true]}})))

(defn add-leaving-allowed-channel
  [{:keys [opts] :as comp} id]
  (far/put-item opts :antlion-clojure-channel
                   {:id id
                    :leaving-allowed? true}))

(defn rm-leaving-allowed-channel
  [{:keys [opts] :as comp} id]
  (far/put-item opts :antlion-clojure-channel
                   {:id id
                    :leaving-allowed? false}))

(defn get-all-reviewers
  [{:keys [opts] :as comp}]
  (far/scan opts :antlion-clojure-user {:attr-conds {:reviewer? [:eq true]}}))

(defn add-reviewer
  [{:keys [opts] :as comp} github-id id]
  (far/put-item opts :antlion-clojure-user
                   {:id id
                    :reviewer? true
                    :github-id github-id}))

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
  (far/put-item opts :antlion-clojure-notify-channel
                   {:name name
                    :channel-id channel-id}))

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
                   {:name name
                    :state state}))

(defn set-lemming-last-state
  [{:keys [opts] :as comp} state]
  (set-last-state comp "lemming" state))

(defn set-response
  [{:keys [opts] :as c} response]
  (far/put-item opts :antlion-clojure-response
                   {:name (:name response)
                    :response-text (:response-text response)
                    :keywords (-> response :keywords)}))

(defn rm-response
  [{:keys [opts] :as c} response-name]
  (far/delete-item opts :antlion-clojure-response {:name response-name}))

(defn get-all-responses
  [{:keys [opts] :as c}]
  (far/scan opts :antlion-clojure-response))

(defn add-stats
  [{:keys [opts] :as c} type stat limit]
  (let [stats (far/get-item opts :antlion-clojure-stats {:type type})]
    (->> (or (:coll stats) [])
         (take-last (dec limit))
         vec
         (#(conj % stat))
         (assoc {:type type} :coll)
         (far/put-item opts :antlion-clojure-stats))))

(defn get-stats
  [{:keys [opts] :as c} type]
  (some->> (far/get-item opts :antlion-clojure-stats {:type type})
           :coll))

(def page-speed-type "page-speed")

(defn add-page-speed
  [{:keys [opts] :as c} url page-speed]
  (add-stats c (str page-speed-type "/" url) page-speed 10))

(defn get-page-speed
  [{:keys [opts] :as c} url]
  (get-stats c (str page-speed-type "/" url)))

(defn add-schedule
  [{:keys [opts] :as c} name {:keys [url channel-id handler-id hour minute second millisecond] :as schedule}]
  (far/put-item opts :antlion-clojure-schedule
                   {:name name
                    :handler-id handler-id
                    :channel-id channel-id
                    :hour hour
                    :minute minute
                    :second second
                    :millisecond millisecond}))

(defn rm-schedule
  [{:keys [opts] :as c} name]
  (far/delete-item opts :antlion-clojure-schedule {:name name}))

(defn get-all-schedule
  [{:keys [opts] :as c}]
  (far/scan opts :antlion-clojure-schedule))

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
