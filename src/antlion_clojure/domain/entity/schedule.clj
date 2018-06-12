(ns antlion-clojure.domain.entity.schedule
  (:require [clojure.spec.alpha :as s]
            [clojure.core.async.impl.protocols :as ap]))

(s/def :schedule/opts map?)
(s/def :schedule/hour (s/and number? #(>= % 0) #(< % 24)))
(s/def :schedule/minute (s/and number? #(>= % 0) #(< % 60)))
(s/def :schedule/second (s/and number? #(>= % 0) #(< % 60)))
(s/def :schedule/millisecond (s/and number? #(>= % 0) #(< % 1000)))

(s/def ::schedule (s/keys :req-un [:schedule/opts
                                   :schedule/hour
                                   :schedule/minute
                                   :schedule/second
                                   :schedule/millisecond]))

(s/def ::schedule-chan #(satisfies? ap/ReadPort %))
