(ns antlion-clojure.bot
  (:require [antlion-clojure.redis :as redis]
            [antlion-clojure.sandbox :refer [parse-form format-result]]
            [environ.core :refer [env]]
            [antlion-clojure.slack :as slack :refer [map->Payload parse-channel]]
            [slack-rtm.core :as rtm]
            [clojure.string :refer [split]]
            [com.stuartsierra.component :as component])
  (:use org.httpkit.server)
  (:gen-class))

(defn from-master?
  [{:keys [master-user-name] :as slack} res]
  (= master-user-name (get-in res [:user_profile :name])))

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
  (let [parse-result (parse-form (-> res :text (split #" " 2) second))
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
                                "\n----------\n"
                                (format-result parse-result))}))))

(defn- set-problem
  [{:keys [master-user-name] :as slack}
   {:keys [user channel] :as res}
   question answer]
  (try
    (do
      (redis/set-problem question (read-string answer))
      (map->Payload {:type :message
                     :user user
                     :channel channel
                     :text (str "ﾄｳﾛｸｼﾀﾖ!\n"
                                "```\n"
                                answer
                                "```")}))
    (catch Exception e
      (map->Payload {:type :message
                     :user user
                     :channel channel
                     :text (str "ｺﾀｴｶﾞｵｶｼｲｰﾈ!\n"
                                "```\n"
                                (.getMessage e)
                                "```")}))))

(defn del-problem
  [{:keys [master-user-name] :as slack}
   {:keys [user user_profile channel] :as res}
   question]
  (cond
    (not (from-master? slack res))
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

(defn- set-fyi
  [{:keys [user user_profile channel] :as res} title information]
  (try
    (do
      (redis/set-fyi user title information)
      (map->Payload {:type :message
                     :user user
                     :channel channel
                     :text (str "ﾄｳﾛｸｼﾀﾖ!\n"
                                "```\n"
                                title ": " information
                                "```")}))
    (catch Exception e
      (map->Payload {:type :message
                     :user user
                     :channel channel
                     :text (str "ﾊﾞｶｼﾞｬﾈｰﾉ\n"
                                "```\n"
                                title ": " (.getMessage e)
                                "```")}))))

(defn- del-fyi
  [{:keys [user user_profile channel] :as res} title]
  (try
    (let [res (redis/del-fyi user title)]
      (if (zero? res)
        (map->Payload {:type :message
                       :user user
                       :channel channel
                       :text (str "ﾐﾂｶﾗﾅｲ−ﾖ!\n"
                                  "```\n"
                                  title
                                  "```")})
        (map->Payload {:type :message
                       :user user
                       :channel channel
                       :text (str "ｹｼﾀﾖ!\n"
                                  "```\n"
                                  title
                                  "```")})))
    (catch Exception e
      (map->Payload {:type :message
                     :user user
                     :channel channel
                     :text (str "ﾊﾞｶｼﾞｬﾈｰﾉ\n"
                                "```\n"
                                title ": " (.getMessage e)
                                "```")}))))

(defn- format-fyi
  [fyis]
  (str "```\n"
       (->>
         (map (fn [[title information]]
                (str title ": " information))
              fyis)
         (clojure.string/join "\n"))
       "\n"
       "```"))

(defn- get-all-fyi
  [{:keys [user user_profile channel] :as res} title]
  (try
    (let [res (redis/get-all-fyi user)]
      (if (seq res)
        (map->Payload {:type :message
                       :user user
                       :channel channel
                       :text (format-fyi res)})
        (map->Payload {:type :message
                       :user user
                       :channel channel
                       :text "ﾊﾞｶｼﾞｬﾈｰﾉ"})))
    (catch Exception e
      (map->Payload {:type :message
                     :user user
                     :channel channel
                     :text (str "ﾊﾞｶｼﾞｬﾈｰﾉ\n"
                                "```\n"
                                title ": " (.getMessage e)
                                "```")}))))

(defn- eval
  [{:keys [user channel] :as res} txt]
  (let [parse-result (parse-form txt)]
    (map->Payload {:type :message
                   :user user
                   :channel channel
                   :text (format-result parse-result)})))

(defn- add-allowed-channel
  [slack
   {:keys [user channel] :as res}
   chan-str]
  (let [c (parse-channel chan-str)]
    (try
      (if (from-master? slack res)
        (do
          (some-> c :id redis/add-leaving-allowed-channel)
          (map->Payload {:type :message
                         :user user
                         :channel channel
                         :text (str "ﾄｳﾛｸｼﾀﾖ!\n"
                                    "```\n"
                                    (:name c)
                                    "```")}))
        (map->Payload {:type :message
                       :user user
                       :channel channel
                       :text "ｹﾝｹﾞﾝｶﾞﾅｲｰﾖ!"}))
      (catch Exception e
        (map->Payload {:type :message
                       :user user
                       :channel channel
                       :text (str "ﾊﾞｶｼﾞｬﾈｰﾉ\n"
                                  "```\n"
                                  (:name c) ": " (.getMessage e)
                                  "```")})))))

(defn- rm-allowed-channel
  [slack
   {:keys [user channel] :as res}
   chan-str]
  (let [c (parse-channel chan-str)]
    (try
      (if (from-master? slack res)
        (do
          (-> c :id redis/rm-leaving-allowed-channel)
          (map->Payload {:type :message
                         :user user
                         :channel channel
                         :text "ｹｼﾀﾖ!"}))
        (map->Payload {:type :message
                       :user user
                       :channel channel
                       :text "ｹﾝｹﾞﾝｶﾞﾅｲｰﾖ!"}))
      (catch Exception e
        (map->Payload {:type :message
                       :user user
                       :channel channel
                       :text (str "ﾊﾞｶｼﾞｬﾈｰﾉ\n"
                                  "```\n"
                                  (:name c) ": " (.getMessage e)
                                  "```")})))))

(defn- help
  [{:keys [user channel] :as res} me]
  (map->Payload {:type :message
                 :user user
                 :channel channel
                 :text
                 (str
                     "ﾂｶｲｶﾀ\n"
                     "```"
                      me " help                          : この文章を表示\n"
                      me " fyi                           : メモ一覧を表示\n"
                      me " set-fyi <title> <body>        : <title> <body>をメモ\n"
                      me " del-fyi <title>               : <title>を削除\n"
                      me " <S-Expression>                : <S-Expression>を評価\n"
                      "------------------ 管理者限定機能 ------------------\n"
                      me " add-allowed-channel <channel> : <channel>を監視対象から外す\n"
                      me " rm-allowed-channel <channel>  : <channel>を監視対象に戻す\n"
                      "```")}))

(defn- channel-leave-handler
  [slack {:keys [user channel] :as res}]
  (cond
    (from-master? slack res)
    (map->Payload {:type :message
                   :user user
                   :channel channel
                   :text "ﾊﾞｲﾊﾞｲ!"})
    (redis/get-leaving-allowed? user)
    (map->Payload {:type :message
                   :user user
                   :channel channel
                   :text "ｲﾝｼﾞｬﾈｰﾉ!"})
    ((set (redis/get-all-leaving-allowed-channels)) channel)
    []
    :else (question res)))

(defn- command-message-handler
  [slack
   {:keys [user channel] :as res}]
  (let [txt (split (:text res) #"\s+")
        me (first txt)
        command (second txt)
        args (drop 2 txt)]
    (case command
      "help" (help res me)
      "set-problem" (set-problem res (first args) (second args))
      "del-problem" (del-problem res (first args))
      "set-fyi" (set-fyi res (first args) (second args))
      "del-fyi" (del-fyi res (first args))
      "add-allowed-channel" (add-allowed-channel slack res (first args))
      "rm-allowed-channel" (rm-allowed-channel slack res (first args))
      ("fyi" "FYI") (get-all-fyi res (first args))
      (map->Payload {:type :message
                     :user user
                     :channel channel
                     :text "ﾊﾞｶｼﾞｬﾈｰﾉ"}))))

(defn- default-message-handler
  [slack
   {:keys [user channel text] :as res}]
  (let [self (redis/get-self)
        txt (some-> text (split #"\s+" 2) second)]
    (println res)
    (when (slack/message-for-me? res self)
      (cond
        (redis/get-checking-question? user)
        (check-question res)
        (= (-> txt first) \()
        (eval res txt)
        :else (command-message-handler slack res)))))

(defn message-handler
  [slack res]
  (->> (case (:subtype res)
         "channel_leave" (channel-leave-handler slack res)
         "group_leave" (channel-leave-handler slack res)
         (default-message-handler slack res))
       (slack/reaction! slack)))

(defn register-events!
  [slack]
  (slack/sub-to-event! slack :message #(message-handler slack %)))

(defn app [req]
  {:status  200
   :headers {"Content-Type" "text/html"}
   :body    "hello HTTP!"})

(defrecord BotComponent [master-user-name port server slack]
  component/Lifecycle
  (start [{:keys [slack] :as this}]
    (println ";; Starting BotComponent")
    (register-events! slack)
    (-> this
        (assoc :server (run-server app {:port port}))))
  (stop [{:keys [server] :as this}]
    (println ";; Stopping BotComponent")
    (when-not (nil? server)
      (server))
    (-> this
        (dissoc :server))))

(defn bot-component [{:keys [master-user-name
                             port
                             allowed-channels]}]
  (map->BotComponent {:master-user-name master-user-name
                      :port port}))
