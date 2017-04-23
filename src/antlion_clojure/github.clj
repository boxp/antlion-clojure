(ns antlion-clojure.github
  (:require [tentacles.core :as core]
            [tentacles.users :as users]
            [tentacles.repos :as repos]
            [tentacles.pulls :as pulls]
            [environ.core :refer [env]]
            [com.stuartsierra.component :as component]))

(defrecord PullRequest
  [owner repo id])

(defn url->pull-request
  [url]
  (some->> url
           (drop 1)
           (drop-last)
           (apply str)
           (re-seq #"https\://github\.com/(.*)/(.*)/pull/([0-9]*)$")
           first
           (drop 1)
           (apply ->PullRequest)))

(defn base-option
  []
  {:oauth-token (env :antlion-clojure-github-oauth-token)
   :accept "application/vnd.github.black-cat-preview+json"})

(defn reviewers
  [pr]
  (->> (pulls/specific-pull (:owner pr) (:repo pr) (:id pr) (base-option))
       :requested_reviewers))

(defn add-reviewer
  [url reviewer]
  (let [pr (url->pull-request url)]
    (core/api-call :post "repos/%s/%s/pulls/%s/requested_reviewers"
                   [(:owner pr) (:repo pr) (:id pr)]
                   (merge (base-option)
                          {:reviewers [reviewer]}))))
