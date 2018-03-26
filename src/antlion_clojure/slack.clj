(ns antlion-clojure.slack
  (:require [gniazdo.core :as ws]
            [clojure.core.async :refer [go-loop <! put!]]
            [clj-slack.usergroups.users :as usergroups-users]
            [clj-slack.chat :as chat]
            [clj-slack.channels :as channels]
            [clj-slack.groups :as groups]
            [slack-rtm.core :as rtm]
            [com.stuartsierra.component :as component]))

(def api-url "https://slack.com/api")

;; REST API
(defrecord Payload
  [type subtype user text channel group optionals])

(defrecord Channel
  [id name])

(defn parse-channel
  [s]
  (some-> (re-matches #"\<(.*)\>" s)
          second
          (clojure.string/split #"\|")
          (update 0 #(->> % (drop 1) (apply str)))
          (#(apply ->Channel %))))

(defn parse-user
  [s]
  (some->> (re-matches #"\<(@.*)\>" s)
           second
           (drop 1)
           (apply str)))

(defn parse-usergroups
  [s]
  (some->> s
           (re-matches #"\<\!subteam\^(.*)\|.*\>")
           second))

(defn message-for-me?
  [{:keys [slack res] :as opt}]
  (when (:text res)
    (re-matches (re-pattern (str "(?s)"
                                 "\\<\\@"
                                 (-> slack :rtm-connection :start :self :id)
                                 "\\> .*"))
                (:text res))))

(defn message-from-me?
  [{:keys [slack res] :as opt}]
  (= (-> slack :rtm-connection :start :self :id)
     (:user res)))

(defn post
  [{:keys [connection]} {:keys [channel text optionals]}]
  (chat/post-message connection channel text (merge {:as_user "true"} optionals)))

(defn reply
  [{:keys [connection]} {:keys [channel text user optionals]}]
  (chat/post-message connection channel (str "<@" user "> " text) (merge {:as_user "true"} optionals)))

(defn channel-invite
  [{:keys [connection invite-token]} {:keys [channel user]}]
  (-> connection
      (assoc :token invite-token)
      (channels/invite channel user)))

(defn group-invite
  [{:keys [connection invite-token]} {:keys [channel user]}]
  (-> connection
      (assoc :token invite-token)
      (groups/invite channel user)))

(defn usergroups-users
  [{:keys [connection invite-token]} usergroups-id]
  (-> connection
      (assoc :token invite-token)
      (usergroups-users/list usergroups-id)
      :users))

(defn- dispatch-message!
  [slack {:keys [subtype user] :as payload}]
  (cond
    (= :channel_invite subtype)
    (channel-invite slack payload)
    (= :group_invite subtype)
    (group-invite slack payload)
    (nil? subtype)
    (if user
      (reply slack payload)
      (post slack payload))
    :else nil))

(defn- dispatch-payload!
  [slack {:keys [type] :as payload}]
  (case type
    :message (dispatch-message! slack payload)
    nil))

(defn reaction!
  [slack payload]
  (cond
    (vector? payload) (doseq [p payload] (dispatch-payload! slack p))
    (map? payload) (dispatch-payload! slack payload)
    :else nil))

(defn send-payload
  [slack payload]
  (let [dispatcher (-> slack :rtm-connection :dispatcher)]
    (cond
      (vector? payload)
      (doseq [p payload] (rtm/send-event dispatcher p))
      (map? payload)
      (rtm/send-event dispatcher payload))))

(defn sub-to-event!
  [slack type f]
  (let [events-publication (-> slack :rtm-connection :events-publication)]
    (rtm/sub-to-event events-publication type f)))

(defrecord SlackComponent
  [rtm-connection connection invite-token]
  component/Lifecycle
  (start [this]
    (println ";; Starting SlackComponent")
    (let [rtm-connection (rtm/connect connection)]
      (-> this
          (assoc :rtm-connection rtm-connection))))
  (stop [{:keys [rtm-connection] :as this}]
      (println ";; Stopping SlackComponent")
      (when-not (nil? rtm-connection)
        (rtm/send-event (:dispatcher rtm-connection) :close))
      (-> this
          (dissoc :rtm-connection))))

(defn slack-component
  [antlion-clojure-token antlion-clojure-invite-token]
  (map->SlackComponent {:connection {:token antlion-clojure-token :api-url api-url}
                        :invite-token antlion-clojure-invite-token}))
