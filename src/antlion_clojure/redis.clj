(ns antlion-clojure.redis
  (:require [environ.core :refer [env]]
            [taoensso.carmine :as car :refer [wcar]]))

(defmacro master-wcar*
  [& body]
  `(wcar {:pool {} :spec {:host (env :redis-master-host)
                          :port (some-> (env :redis-master-port) Integer.)}}
            ~@body))

(defmacro slave-wcar*
  [& body]
  `(wcar {:pool {} :spec {:host (env :redis-slave-host)
                          :port (some-> (env :redis-slave-port) Integer.)}}
            ~@body))

(def key-leaving-allowed?
  "is_leaving_allowed/")

(defn get-leaving-allowed?
  [user]
  (slave-wcar* (car/get (str key-leaving-allowed? user))))

(defn set-leaving-allowed?
  [user]
  (master-wcar* (car/set (str key-leaving-allowed? user) "true")))

(defn del-leaving-allowed?
  [user]
  (master-wcar* (car/del (str key-leaving-allowed? user))))

(def key-checking-question?
  "is_checking_question/")

(defn set-checking-question?
  [user question]
  (master-wcar* (car/set (str key-checking-question? user) question)))

(defn get-checking-question?
  [user]
  (slave-wcar* (car/get (str key-checking-question? user))))

(defn del-checking-question?
  [user]
  (master-wcar* (car/del (str key-leaving-allowed? user))))

(def key-problem
  "problems")

(defn set-problem
  [question answer]
  (master-wcar* (car/hset key-problem question (str answer))))

(defn get-problem
  [question]
  (some-> (slave-wcar* (car/hget key-problem question))
          read-string))

(defn get-all-problem []
  (slave-wcar* (car/hgetall* key-problem)))

(defn del-problem
  [problem]
  (master-wcar* (car/hdel key-problem problem)))

(def key-self
  "self")

(defn set-self
  [self]
  (master-wcar* (car/set key-self self)))

(defn get-self []
  (slave-wcar* (car/get key-self)))

(def key-fyi
  "fyi/")

(defn set-fyi
  [user title information]
  (master-wcar* (car/hset (str key-fyi user) title information)))

(defn del-fyi
  [user title]
  (master-wcar* (car/hdel (str key-fyi user) title)))

(defn get-all-fyi
  [user]
  (slave-wcar* (car/hgetall* (str key-fyi user))))

(def key-leaving-allowed-channels
  "leaving_allowed_channels")

(defn add-leaving-allowed-channel
  [channel-id]
  (master-wcar* (car/sadd key-leaving-allowed-channels channel-id)))

(defn rm-leaving-allowed-channel
  [channel-id]
  (master-wcar* (car/srem key-leaving-allowed-channels channel-id)))

(defn get-all-leaving-allowed-channels []
  (slave-wcar* (car/smembers key-leaving-allowed-channels)))
