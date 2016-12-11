(ns antlion-clojure.sandbox
  (:require [clojail.core :refer [sandbox]]
            [clojail.testers :refer [secure-tester-without-def blanket]]
            [clojure.string :as string])
  (:import java.io.StringWriter))

(def my-tester
  (conj secure-tester-without-def (blanket "@shanghai")))

(def sb (sandbox my-tester))

(defrecord Result [result output error])

(defn- decode-string
  [s]
  (string/replace s #"\&lt\;|\&gt\;|\&" {"&lt;" "<", "&gt;" ">", "&amp;" "&"}))

(defn- report-error
  [form e]
  (println "Something wrong to " form " " (.getMessage e)))

(defn parse-form
  [form]
  (try
    (with-open [out (StringWriter.)]
      (-> form
          decode-string
          read-string
          (sb {#'*out* out})
          (Result. out nil)))
    (catch Exception e
      (do
        (report-error form e)
        (Result. (.getMessage e) nil e)))))

(defn format-result
  [res]
  (str "```"
       (when-let [o (:output res)]
         o)
       (if (nil? (:result res))
         "nil"
         (:result res))
       "```"))
