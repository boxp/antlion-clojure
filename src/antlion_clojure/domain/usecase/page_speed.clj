(ns antlion-clojure.domain.usecase.page-speed
  (:require [clojure.spec.alpha :as s]
            [com.stuartsierra.component :as component]
            [antlion-clojure.infra.repository.page-speed :as repo]
            [antlion-clojure.slack :as slack]
            [antlion-clojure.dynamodb :as dynamodb]))

(s/def :page-speed-usecase/slack record?)
(s/def :page-speed-usecase/dynamodb record?)
(s/def ::page-speed-usecase
  (s/keys :req-un [::repo/page-speed-repository
                   :page-speed-usecase/slack
                   :page-speed-usecase/dynamodb]))

(def pairs-lv-url "https://pairs.lv")
(def www-pairs-lv-url "https://www.pairs.lv")

(s/fdef post-daily-report
  :args (s/cat :c ::page-speed-usecase)
  :ret nil?)
(defn post-daily-report
  [{:keys [page-speed-repository slack dynamodb]} channel-id]
  (try
    (let [[www-pairs-page-speed pairs-lv-page-speed]
          (pmap #(repo/fetch-page-speed page-speed-repository %)
                [www-pairs-lv-url pairs-lv-url])
          [www-pairs-page-speed-stats pairs-lv-page-speed-stats]
          (pmap #(dynamodb/get-page-speed dynamodb %)
                [www-pairs-lv-url pairs-lv-url])
          www-pairs-page-speed-last-stat (last www-pairs-page-speed-stats)
          pairs-lv-page-speed-last-stat (last pairs-lv-page-speed-stats)]
      (slack/reaction! slack
                       {:type :message
                        :channel channel-id
                        :text (str ":sonic: *今日のPageSpeedInsights* :sonic:"
                                   "```\n"
                                   www-pairs-lv-url ": " (if www-pairs-page-speed-last-stat
                                                           (str (:score www-pairs-page-speed) "(前日比:" (- (:score www-pairs-page-speed) (:score www-pairs-page-speed-last-stat)) ")")
                                                           (:score www-pairs-page-speed)) "\n"
                                   pairs-lv-url ": " (if pairs-lv-page-speed-last-stat
                                                       (str (:score pairs-lv-page-speed) "(前日比:" (- (:score pairs-lv-page-speed) (:score pairs-lv-page-speed-last-stat)) ")")
                                                       (:score pairs-lv-page-speed)) "\n"
                                   "```")})
      (dynamodb/add-page-speed dynamodb www-pairs-lv-url www-pairs-page-speed)
      (dynamodb/add-page-speed dynamodb pairs-lv-url pairs-lv-page-speed))
    (catch Exception e
      (println (ex-info "PageSpeedInsights取得失敗"
                        {:error e})))))

(defrecord PageSpeedUsecase [page-speed-repository slack dynamodb]
  component/Lifecycle
  (start [this] this)
  (stop [this] this))
