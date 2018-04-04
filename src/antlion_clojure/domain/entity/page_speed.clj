(ns antlion-clojure.domain.entity.page-speed
  (:require [clojure.spec.alpha :as s]))

(s/def :page-speed/score number?)
(s/def ::page-speed (s/keys :req-un [:page-speed/score]))
