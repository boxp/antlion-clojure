(ns antlion-clojure.redis
  (:require [environ.core :refer [env]]
            [taoensso.carmine :as car :refer [wcar]]))

(defmacro wcar*
  [& body]
  `(car/wcar {:pool {} :spec {:uri (env :redis-url)}}
             ~@body))

(def key-leaving-allowed?
  "is_leaving_allowed/")

(defn get-leaving-allowed?
  [user]
  (wcar* (car/get (str key-leaving-allowed? user))))

(defn set-leaving-allowed?
  [user]
  (wcar* (car/set (str key-leaving-allowed? user) "true")))

(defn del-leaving-allowed?
  [user]
  (wcar* (car/del (str key-leaving-allowed? user))))

(def key-checking-question?
  "is_checking_question")

(defn set-checking-question?
  [user question]
  (wcar* (car/set (str key-checking-question? user) question)))

(defn get-checking-question?
  [user]
  (wcar* (car/get (str key-checking-question? user))))

(defn del-checking-question?
  [user]
  (wcar* (car/del (str key-leaving-allowed? user))))

(def key-problem
  "problems")

(defn set-problem
  [question answer]
  (wcar* (car/hset key-problem question (str answer))))

(defn get-problem
  [question]
  (some-> (wcar* (car/hget key-problem question))
          read-string))

(defn get-all-problem []
  (wcar* (car/hgetall key-problem)))

(defn del-problem
  [problem]
  (wcar* (car/hdel key-problem problem)))

(def key-self
  "self")

(defn set-self
  [self]
  (wcar* (car/set key-self self)))

(defn get-self []
  (wcar* (car/get key-self)))
