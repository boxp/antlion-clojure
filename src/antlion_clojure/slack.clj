(ns antlion-clojure.slack
  (:require [antlion-clojure.redis :as redis]
            [environ.core :refer [env]]
            [gniazdo.core :as ws]
            [clojure.core.async :refer [go-loop <! put!]]
            [clj-slack.chat :as chat]
            [clj-slack.channels :as channels]
            [clj-slack.groups :as groups]
            [slack-rtm.core :as rtm]))

;; REST API
(defrecord Payload
  [type subtype user text channel group])

(def connection
  {:api-url "https://slack.com/api"
   :token (env :antlion-clojure-token)})

(defn message-for-me?
  [res self]
  (re-matches (re-pattern (str "\\<\\@" self "\\> .*"))
              (:text res)))

(defn post
  [{:keys [channel text]}]
  (chat/post-message connection channel text {:as_user "true"}))

(defn reply
  [{:keys [channel text user]}]
  (chat/post-message connection channel (str "<@" user "> " text) {:as_user "true"}))

(defn channel-invite
  [{:keys [channel user]}]
  (-> connection
      (assoc :token (env :antlion-clojure-invite-token))
      (channels/invite channel user)))

(defn group-invite
  [{:keys [channel user]}]
  (-> connection
      (assoc :token (env :antlion-clojure-invite-token))
      (groups/invite channel user)))

(defn- dispatch-message!
  [{:keys [subtype user]
    :as payload}]
  (cond
    (= :channel_invite subtype)
    (channel-invite payload)
    (= :group_invite subtype)
    (group-invite payload)
    (nil? subtype)
    (if user
      (reply payload)
      (post payload))
    :else nil))

(defn- dispatch-payload!
  [{:keys [type]
    :as payload}]
  (case type
    :message (dispatch-message! payload)
    nil))

(defn reaction!
  [payload]
  (cond
    (vector? payload) (doseq [p payload] (dispatch-payload! p))
    (map? payload) (dispatch-payload! payload)
    :else nil))

;; RTM API
(def rtm-connection
  (atom nil))

(defn send-payload
  [payload]
  (let [dispatcher (:dispatcher @rtm-connection)]
    (cond
      (vector? payload)
      (doseq [p payload] (rtm/send-event dispatcher p))
      (map? payload)
      (rtm/send-event dispatcher payload))))

(defn sub-to-event!
  [type f]
  (let [events-publication (:events-publication @rtm-connection)]
    (rtm/sub-to-event events-publication type f)))

(defn start []
  (when-not @rtm-connection
    (reset! rtm-connection (rtm/connect (env :antlion-clojure-token)))
    (redis/set-self (-> @rtm-connection :start :self :id))))

(defn stop []
  (when @rtm-connection
    (rtm/send-event (:dispatcher @rtm-connection) :close)
    (reset! rtm-connection nil)))

(defn restart []
  (do
    (stop)
    (start)))
