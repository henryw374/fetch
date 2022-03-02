(ns lambdaisland.fetch.edn
  "EDN read/write support

  Split out so as not to blow up the build if EDN support isn't needed."
  (:require [clojure.edn :as edn]
            [lambdaisland.fetch :as fetch]))

(defmethod fetch/encode-body :edn [_ body opts]
  (pr-str body))

(defmethod fetch/decode-body :edn [_ bodyp opts]
  (->
    (.text ^js bodyp)
    (.then
      (fn [text]
        (edn/read-string text)))))
