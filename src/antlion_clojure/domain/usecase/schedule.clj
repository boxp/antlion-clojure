(ns antlion-clojure.domain.usecase.schedule
  (:require [clojure.spec.alpha :as s]
            [antlion-clojure.domain.entity.schedule :as ent]
            [antlion-clojure.infra.repository.schedule :as repo]))

(s/fdef subscribe-schedule
  :args (s/cat :schedule ::ent/schedule)
  :ret ::ent/schedule-chan)
(defn subscribe-schedule
  [schedule]
  (repo/subscribe-schedule schedule))
