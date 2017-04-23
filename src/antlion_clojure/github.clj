(ns antlion-clojure.github
  (:require [tentacles.core :as core]
            [tentacles.users :as users]
            [tentacles.repos :as repos]
            [environ.core :refer [env]]
            [com.stuartsierra.component :as component]))

(defn base-option
  []
  {:oauth-token (env :antlion-clojure-github-oauth-token)
   :accept "application/vnd.github.black-cat-preview+json"})

(defn reviewers
  [url]
  (core/api-call :get "%s/requested_reviewers"
                 (base-option)))
