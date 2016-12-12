(ns antlion-clojure.bot
  (:require [antlion-clojure.redis :as redis]
            [antlion-clojure.sandbox :refer [parse-form format-result]]
            [environ.core :refer [env]]
            [antlion-clojure.slack :as slack :refer [map->Payload]]
            [slack-rtm.core :as rtm]
            [clojure.string :refer [split]])
  (:use org.httpkit.server))

(defn question
  [{:keys [user channel subtype]
    :as res}]
  (let [problems (redis/get-all-problem)
        [question answer] (when (not-empty problems) (rand-nth (vec problems)))]
    (if question
      (do (redis/set-checking-question? user question)
          [(map->Payload {:type :message
                          :user user
                          :channel channel
                          :text (str "ﾄｲﾃﾈ!\n----------\n"
                                     question)})
           (map->Payload {:type :message
                          :user user
                          :subtype
                          (if (= "channel_leave" subtype)
                            :channel_invite
                            :group_invite)
                          :channel channel})])
      (map->Payload {:type :message
                     :user user
                     :channel channel
                     :text "ｳﾝｶﾞﾖｶｯﾀﾅ...MPｶﾞﾀﾘﾅｶｯﾀ"}))))

(defn check-question
  [{:keys [user channel]
    :as res}]
  (let [parse-result (parse-form (-> res :text (split #" ") second))
        question (redis/get-checking-question? user)
        answer (redis/get-problem question)]
    (if (= (:result parse-result) answer)
      (do (redis/del-checking-question? user)
          (redis/set-leaving-allowed? user)
          (map->Payload {:type :message
                         :user user
                         :channel channel
                         :text (str "ｽｺﾞｲﾝｼﾞｬﾈｰﾉ!\n----------\n"
                                    (format-result parse-result))}))
      (map->Payload {:type :message
                     :user user
                     :channel channel
                     :text (str "ｻﾞﾝﾈﾝ!\n"
                                question
                                "----------\n"
                                (format-result parse-result))}))))

(defn- set-problem
  [{:keys [user channel] :as res}
   question answer]
  (try
    (do
      (redis/set-problem question (read-string answer))
      (map->Payload {:type :message
                     :user user
                     :channel channel
                     :text (str "ﾄｳﾛｸｼﾀﾖ!\n----------\n"
                                answer)}))
    (catch Exception e
      (map->Payload {:type :message
                     :user user
                     :channel channel
                     :text (str "ｺﾀｴｶﾞｵｶｼｲｰﾈ!\n----------\n"
                                (.getMessage e))}))))

(defn del-problem
  [{:keys [user user_profile channel] :as res} question]
  (cond
    (not= (:name user_profile) (env :antlion-clojure-master-user-name))
    (map->Payload {:type :message
                   :user user
                   :channel channel
                   :text "ｹﾝｹﾞﾝｶﾞﾅｲｰﾖ!"})
    (zero? (redis/del-problem question))
    (map->Payload {:type :message
                   :user user
                   :channel channel
                   :text "ﾐﾂｶﾗﾅｲﾖ!"})
    :else
    (map->Payload {:type :message
                   :user user
                   :channel channel
                   :text "ｹｼﾀﾖ!"})))

(defn- channel-leave-handler
  [{:keys [user channel]
    :as res}]
  (cond
    (redis/get-leaving-allowed? user)
    (map->Payload {:type :message
                   :user user
                   :channel channel
                   :text "ｲﾝｼﾞｬﾈｰﾉ!"})
    :else (question res)))

(defn- command-message-handler
  [{:keys [user channel]
    :as res}]
  (let [txt (split (:text res) #" ")
        command (second txt)
        args (drop 2 txt)]
    (case command
      "set-problem" (set-problem res (first args) (second args))
      "del-problem" (del-problem res (first args))
      (map->Payload {:type :message
                     :user user
                     :channel channel
                     :text "ﾊﾞｶｼﾞｬﾈｰﾉ"}))))

(defn- default-message-handler
  [{:keys [user channel]
    :as res}]
  (let [self (redis/get-self)]
    (when (slack/message-for-me? res self)
      (cond
        (redis/get-checking-question? user)
        (check-question res)
        :else (command-message-handler res)))))

(defn message-handler
  [res]
  (-> (case (:subtype res)
        "channel_leave" (channel-leave-handler res)
        "group_leave" (channel-leave-handler res)
        (default-message-handler res))
      slack/reaction!))

(defn register-events! []
  (slack/sub-to-event! :message message-handler))

(defn reload []
  (slack/restart)
  (register-events!))

(defn app [req]
  {:status  200
   :headers {"Content-Type" "text/html"}
   :body    "hello HTTP!"})

(defn -main [& args]
  (slack/start)
  (register-events!)
  (run-server app {:port (Integer/parseInt (or (env :port) "3000"))}))
