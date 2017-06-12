(ns antlion-clojure.bot
  (:require [clojure.core.async :refer [go-loop <!]]
            [antlion-clojure.dynamodb :as dynamodb]
            [antlion-clojure.sandbox :refer [parse-form format-result]]
            [environ.core :refer [env]]
            [antlion-clojure.slack :as slack :refer [map->Payload parse-channel]]
            [antlion-clojure.github :as github]
            [antlion-clojure.infra.repository.lemming :refer [subscribe]]
            [slack-rtm.core :as rtm]
            [clojure.string :refer [split]]
            [clojure.set :refer [intersection]]
            [com.stuartsierra.component :as component])
  (:gen-class))

(defn from-master?
  [{:keys [slack dynamodb res] :as opt}]
  (let [{:keys [master-user-name]} slack]
    (= master-user-name (get-in res [:user_profile :name]))))

(defn question
  [{:keys [slack dynamodb res] :as opt}]
  (let [{:keys [user channel subtype]} res
        problems (dynamodb/get-all-problem dynamodb)
        {:keys [question answer]} (when (not-empty problems) (rand-nth (vec problems)))]
    (if question
      (do (dynamodb/set-checking-question? user question)
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
  [{:keys [slack dynamodb res] :as opt}]
  (let [{:keys [user channel]} res
        parse-result (parse-form (-> res :text (split #" " 2) second))
        question (dynamodb/get-checking-question? dynamodb user)
        answer (dynamodb/get-problem dynamodb question)]
    (if (= (:result parse-result) answer)
      (do (dynamodb/rm-checking-question? dynamodb user)
          (dynamodb/set-leaving-allowed? dynamodb user)
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
  [{:keys [slack dynamodb res] :as opt}
   question answer]
  (let [{:keys [master-user-name]} slack
        {:keys [user channel]} res]
    (try
      (do
        (dynamodb/set-problem dynamodb question answer)
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
                                  "```")})))))

(defn rm-problem
  [{:keys [slack dynamodb res] :as opt}
   question]
  (let [{:keys [master-user-name]} slack
        {:keys [user user_profile channel]} res]
    (dynamodb/rm-problem dynamodb question)
    (cond
      (not (from-master? opt))
      (map->Payload {:type :message
                     :user user
                     :channel channel
                     :text "ｹﾝｹﾞﾝｶﾞﾅｲｰﾖ!"})
      :else
      (map->Payload {:type :message
                     :user user
                     :channel channel
                     :text "ｹｼﾀﾖ!"}))))

(defn- set-fyi
  [{:keys [slack dynamodb res] :as opt}
   title information]
  (let [{:keys [user user_profile channel]} res]
    (try
      (do
        (dynamodb/set-fyi dynamodb user title information)
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
                                  "```")})))))

(defn- rm-fyi
  [{:keys [slack dynamodb res] :as opt} title]
  (let [{:keys [user user_profile channel]} res]
    (try
      (let [res (dynamodb/rm-fyi dynamodb user title)]
        (map->Payload {:type :message
                       :user user
                       :channel channel
                       :text (str "ｹｼﾀﾖ!\n"
                                  "```\n"
                                  title
                                  "```")}))
      (catch Exception e
        (map->Payload {:type :message
                       :user user
                       :channel channel
                       :text (str "ﾊﾞｶｼﾞｬﾈｰﾉ\n"
                                  "```\n"
                                  title ": " (.getMessage e)
                                  "```")})))))

(defn- format-fyi
  [fyis]
  (str "```\n"
       (->>
         (map (fn [{:keys [title information]}]
                (str title ": " information))
              fyis)
         (clojure.string/join "\n"))
       "\n"
       "```"))

(defn- get-all-fyi
  [{:keys [slack dynamodb res] :as opt} title]
  (let [{:keys [user user_profile channel]} res]
    (try
      (let [res (dynamodb/get-all-fyi dynamodb user)]
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
                                  "```")})))))

(defn- eval
  [{:keys [slack dynamodb res] :as opt} txt]
  (let [{:keys [user channel]} res
        parse-result (parse-form txt)]
    (map->Payload {:type :message
                   :user user
                   :channel channel
                   :text (format-result parse-result)})))

(defn- add-allowed-channel
  [{:keys [slack dynamodb res] :as opt}
   chan-str]
  (let [{:keys [user channel]} res
        c (parse-channel chan-str)]
    (try
      (if (from-master? opt)
        (do
          (some->> c :id (dynamodb/add-leaving-allowed-channel dynamodb))
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
  [{:keys [slack dynamodb res] :as opt} chan-str]
  (let [{:keys [user channel]} res
        c (parse-channel chan-str)]
    (try
      (if (from-master? opt)
        (do
          (some->> c :id (dynamodb/rm-leaving-allowed-channel dynamodb))
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

(defn- add-reviewer
  [{:keys [slack dynamodb res] :as opt}
   slack-user
   github-user]
  (let [{:keys [user channel]} res]
    (if (from-master? opt)
      (do
        (some->> slack-user slack/parse-user (dynamodb/add-reviewer dynamodb github-user))
        (map->Payload {:type :message
                       :user user
                       :channel channel
                       :text (str "ﾄｳﾛｸｼﾀﾖ!\n"
                                  "```\n"
                                  "slack: " slack-user "\n"
                                  "github " github-user "\n"
                                  "```")}))
      (map->Payload {:type :message
                     :user user
                     :channel channel
                     :text "ｹﾝｹﾞﾝｶﾞﾅｲｰﾖ!"}))))

(defn- rm-reviewer
  [{:keys [slack dynamodb res] :as opt} u]
  (let [{:keys [user channel]} res]
    (if (from-master? opt)
      (do
        (some->> u slack/parse-user (dynamodb/rm-reviewer dynamodb))
        (map->Payload {:type :message
                       :user user
                       :channel channel
                       :text (str "ｹｼﾀﾖ!\n"
                                  "```\n"
                                  u
                                  "```")}))
      (map->Payload {:type :message
                     :user user
                     :channel channel
                     :text "ｹﾝｹﾞﾝｶﾞﾅｲｰﾖ!"}))))

(defn- remove-me
  [reviewers res]
  (remove #(= (-> res :user) (:id %)) reviewers))

(defn- get-reviewer
  [{:keys [slack dynamodb res] :as opt} usergroups-str]
  (let [usergroups-users (some->> usergroups-str
                                  slack/parse-usergroups
                                  (slack/usergroups-users slack))
        reviewers (-> (dynamodb/get-all-reviewers dynamodb) (remove-me res))]
    (if (seq usergroups-users)
      (some->> reviewers
               (filter #((set usergroups-users) (:id %)))
               seq
               rand-nth)
      (some->> reviewers
               seq
               rand-nth))))


(defn- review
  [{:keys [slack dynamodb res] :as opt} pr usergroups-str]
  (let [{:keys [user channel]} res
        reviewer (get-reviewer opt usergroups-str)
        {:keys [id github-id]} reviewer]
    (cond (not reviewer) (map->Payload {:type :message
                                        :user user
                                        :channel channel
                                        :text (str "ﾚﾋﾞｭﾜｰｶﾞｲﾅｲﾖ!")})

          (not pr) (map->Payload {:type :message
                                  :user user
                                  :channel channel
                                  :text (str "prﾁｮｳﾀﾞｲ!")})
          :else
          (do
            (github/add-reviewer pr github-id)
            (map->Payload {:type :message
                           :user id
                           :channel channel
                           :text (str "っ＝[レビューをお願いします]\n")})))))

(defn- set-lemming-channel
  [{:keys [slack dynamodb res] :as opt} channel-str]
  (let [channel (parse-channel channel-str)]
    (dynamodb/set-lemming-channel dynamodb (:id channel))
    (map->Payload {:type :message
                   :user (:user res)
                   :channel (:channel res)
                   :text (str "ﾄｳﾛｸｼﾀﾖ!\n"
                              "```\n"
                              "channel: " (:name channel) "\n"
                              "```")})))

(defn- get-lemming-last-state
  [{:keys [slack dynamodb res] :as opt}]
  (let [last-state (:state (dynamodb/get-lemming-last-state dynamodb))]
    (map->Payload {:type :message
                   :user (:user res)
                   :channel (:channel res)
                   :text (str "っ＝[" last-state "ppm]")})))

(defn- help
  [{:keys [user channel] :as res} me]
  (map->Payload {:type :message
                 :user user
                 :channel channel
                 :text
                 (str
                     "ﾂｶｲｶﾀ\n"
                     "```"
                      me " help                                    : この文章を表示\n"
                      me " fyi                                     : メモ一覧を表示\n"
                      me " set-fyi <title> <body>                  : <title> <body>をメモ\n"
                      me " rm-fyi <title>                          : <title>を削除\n"
                      me " set-lemming-channel <channel>           : <channel>でCO2濃度の警告を出す\n"
                      me " get-co2                                 : CO2濃度を確認する\n"
                      me " review <pr> <usergroup?>                : <pr>を誰かに割り振る(<usergroup?>に絞る事が出来る)\n"
                      me " <S-Expression>                          : <S-Expression>を評価\n"
                      "------------------ 管理者限定機能 ------------------\n"
                      me " add-allowed-channel <channel>           : <channel>を監視対象から外す\n"
                      me " rm-allowed-channel <channel>            : <channel>を監視対象に戻す\n"
                      me " add-reviewer <slack-user> <github-user> : <slack-user>をレビュワーに登録する\n"
                      me " rm-reviewer <slack-user>                : <slack-user>をレビュワーから外す\n"
                      me " set-problem <problem> <answer>          : <problem>を出題リストに登録する\n"
                      me " rm-problem <problem>                    : <problem>を出題リストから外す\n"
                      "```")}))

(defn- channel-leave-handler
  [{:keys [slack dynamodb res] :as opt}]
  (let [{:keys [user channel]} res]
    (cond
      (from-master? opt)
      (map->Payload {:type :message
                     :user user
                     :channel channel
                     :text "ﾊﾞｲﾊﾞｲ!"})
      (dynamodb/get-leaving-allowed? dynamodb user)
      (map->Payload {:type :message
                     :user user
                     :channel channel
                     :text "ｲﾝｼﾞｬﾈｰﾉ!"})
      ((set (dynamodb/get-all-leaving-allowed-channels dynamodb)) channel)
      []
      :else (question opt))))

(defn- command-message-handler
  [{:keys [slack dynamodb res] :as opt}]
  (let [{:keys [user channel]} res
        txt (split (:text res) #"\s+")
        me (first txt)
        command (second txt)
        args (drop 2 txt)]
    (case command
      "help" (help res me)
      "set-problem" (set-problem opt (first args) (second args))
      "rm-problem" (rm-problem opt (first args))
      "set-fyi" (set-fyi opt (first args) (second args))
      "rm-fyi" (rm-fyi opt (first args))
      "set-lemming-channel" (set-lemming-channel opt (first args))
      "get-co2" (get-lemming-last-state opt)
      ("fyi" "FYI") (get-all-fyi opt (first args))
      "review" (review opt (first args) (second args))
      "add-allowed-channel" (add-allowed-channel opt (first args))
      "rm-allowed-channel" (rm-allowed-channel opt (first args))
      "add-reviewer" (add-reviewer opt (first args) (second args))
      "rm-reviewer" (rm-reviewer opt (first args))
      (map->Payload {:type :message
                     :user user
                     :channel channel
                     :text "ﾊﾞｶｼﾞｬﾈｰﾉ"}))))

(defn- default-message-handler
  [{:keys [slack dynamodb res] :as opt}]
  (let [{:keys [user channel text]} res
        txt (some-> text (split #"\s+" 2) second)]
    (when (slack/message-for-me? opt)
      (cond
        (dynamodb/get-checking-question? dynamodb user)
        (check-question opt)
        (= (-> txt first) \()
        (eval opt txt)
        :else (command-message-handler opt)))))

(defn message-handler
  [{:keys [slack dynamodb res] :as opt}]
  (->> (case (:subtype res)
         "channel_leave" (channel-leave-handler opt)
         "group_leave" (channel-leave-handler opt)
         (default-message-handler opt))
       (slack/reaction! slack)))

(def max-ppm 1000)

(defn lemming-handler
  [{:keys [slack dynamodb res] :as opt} co2]
  (let [channel-id (some-> (dynamodb/get-lemming-channel dynamodb) :channel-id)
        last-state (some-> (dynamodb/get-lemming-last-state dynamodb) :state)]
    (dynamodb/set-lemming-last-state dynamodb (:value co2))
    (when (and
            channel-id
            last-state
            (> max-ppm last-state)
            (> (:value co2) max-ppm))
      (slack/reaction! slack
        (map->Payload
          {:type :message
           :channel channel-id
           :text (str "ﾜｰﾆﾝ!つ = [Co2濃度" max-ppm "ppmを超えました]")})))))

(defn register-events!
  [{:keys [slack dynamodb] :as opt}]
  (slack/sub-to-event! slack :message #(message-handler (merge opt {:res %}))))

(defn app [req]
  {:status  200
   :headers {"Content-Type" "text/html"}
   :body    "hello HTTP!"})

(defrecord BotComponent [master-user-name port server slack dynamodb lemming-repository]
  component/Lifecycle
  (start [{:keys [slack dynamodb lemming-repository] :as this}]
    (println ";; Starting BotComponent")
    (register-events! {:slack slack
                       :dynamodb dynamodb})
    (go-loop [in {:value (or (some-> (dynamodb/get-lemming-last-state dynamodb) :state)
                             0)}]
      (when in
        (lemming-handler this in)
        (recur (<! (subscribe lemming-repository)))))
    this)
  (stop [this]
    (println ";; Stopping BotComponent")
    this))

(defn bot-component [{:keys [master-user-name
                             allowed-channels]}]
  (map->BotComponent {:master-user-name master-user-name}))
