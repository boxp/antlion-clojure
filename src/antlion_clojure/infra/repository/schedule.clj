(ns antlion-clojure.infra.repository.schedule
  (:require [clojure.spec.alpha :as s]
            [chime :refer [chime-ch]]
            [clj-time.core :as t]
            [clj-time.periodic :refer [periodic-seq]]
            [antlion-clojure.domain.entity.schedule :as ent])
  (:import [org.joda.time DateTimeZone]))

(s/fdef subscribe-daily-schedule
  :args (s/cat :schedule ::ent/schedule)
  :ret ::ent/schedule-chan)
(defn subscribe-daily-schedule
  [{:keys [hour minute second millisecond] :as schedule}]
  (->> (periodic-seq (.. (t/now)
                         (withZone (DateTimeZone/forID "Asia/Tokyo"))
                         (withTime hour minute second millisecond))
                     (-> 1 t/days))
       chime-ch))
