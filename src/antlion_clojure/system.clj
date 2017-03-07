(ns antlion-clojure.system
  (:require [com.stuartsierra.component :as component]
            [environ.core :refer [env]]
            [antlion-clojure.slack :refer [slack-component]]
            [antlion-clojure.bot :refer [bot-component]])
  (:gen-class))

(defn antlion-clojure-system
  [{:keys [antlion-clojure-token
           antlion-clojure-invite-token
           master-user-name
           port]
    :as config-options}]
  (component/system-map
    :slack (slack-component antlion-clojure-token antlion-clojure-invite-token)
    :bot (component/using
           (bot-component master-user-name port)
           [:slack])))

(defn load-config []
  {:antlion-clojure-token (env :antlion-clojure-token)
   :antlion-clojure-invite-token (env :antlion-clojure-invite-token)
   :master-user-name (env :antlion-clojure-master-user-name)
   :port (or (env :port) 3000)})

(defn -main []
  (component/start
    (antlion-clojure-system (load-config))))
