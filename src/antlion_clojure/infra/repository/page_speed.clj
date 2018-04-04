(ns antlion-clojure.infra.repository.page-speed
  (:require [environ.core :refer [env]]
            [clojure.spec.alpha :as s]
            [com.stuartsierra.component :as component]
            [clj-http.client :as client]
            [antlion-clojure.domain.entity.page-speed :as ent]))

(s/def ::api-key string?)
(s/def ::page-speed-repository
  (s/keys :req-un [::api-key]))

(def page-speed-insights-url
  "https://www.googleapis.com/pagespeedonline/v4/runPagespeed")

(s/fdef fetch-page-speed
  :args (s/cat :c ::page-speed-repository
               :url string?)
  :ret ::ent/page-speed)
(defn fetch-page-speed
  [{:keys [api-key] :as c} url]
  (let [res (client/get page-speed-insights-url
                        {:as :json
                         :query-params {"url" url
                                        "locale" "ja_JP"
                                        "key" api-key}})]
    {:score (-> res :body :ruleGroups :SPEED :score)}))

(defrecord PageSpeedRepository [api-key]
  component/Lifecycle
  (start [this]
    this)
  (stop [this]
    this))
