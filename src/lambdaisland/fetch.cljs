(ns lambdaisland.fetch
  (:refer-clojure :exclude [get])
  (:require [clojure.core :as c]
            [clojure.set :as set]
            [clojure.string :as str]
            [cognitect.transit :as transit]
            [lambdaisland.uri :as uri]
            [lambdaisland.uri.normalize :as uri-normalize]))

;; fetch(url, {
;;             method: 'POST', // *GET, POST, PUT, DELETE, etc.
;;             mode: 'cors', // no-cors, *cors, same-origin
;;             cache: 'no-cache', // *default, no-cache, reload, force-cache, only-if-cached
;;             credentials: 'same-origin', // include, *same-origin, omit
;;             headers: {
;;                       'Content-Type': 'application/json'
;;                       // 'Content-Type': 'application/x-www-form-urlencoded',
;;                       },
;;             redirect: 'follow', // manual, *follow, error
;;             referrerPolicy: 'no-referrer', // no-referrer, *client
;;             body: JSON.stringify(data) // body data type must match "Content-Type" header
;;             });

(def content-types
  {:transit-json "application/transit+json"
   :json         "application/json"
   :form-encoded "application/x-www-form-urlencoded"
   :text         "text/plain"
   :html         "text/html"
   :edn          "application/edn"})

(def transit-json-writer
  (delay (transit/writer :json)))

(def transit-json-reader
  (delay (transit/reader :json)))

(defmulti encode-body (fn [content-type body opts] content-type))

(defmethod encode-body :default [_ body opts]
  body)

(defmethod encode-body :transit-json [_ body opts]
  (transit/write (:transit-json-writer opts @transit-json-writer) body))

(defmethod encode-body :form-encoded [_ body opts]
  (uri/map->query-string body))

(defmethod encode-body :json [_ body opts]
  (js/JSON.stringify (clj->js body)))

(defmulti decode-body (fn [content-type bodyp opts] content-type))

(defmethod decode-body :default [_ response opts]
  (.text ^js response))

(defmethod decode-body :transit-json [_ response opts]
  (->
    (.text ^js response)
    (.then
      (fn [text]
        (let [decoded (transit/read (:transit-json-reader opts @transit-json-reader) text)]
          (if (satisfies? IWithMeta decoded)
            (vary-meta decoded assoc ::raw text)
            decoded))))))

(defmethod decode-body :json [_ response opts]
  (.json ^js response))

(defn fetch-opts [{:keys [method accept content-type
                          headers redirect mode cache signal
                          credentials referrer-policy]
                   :or   {method          :get
                          accept          :transit-json
                          content-type    :transit-json
                          redirect        :follow
                          mode            :cors
                          cache           :default
                          credentials     :same-origin
                          referrer-policy :client}}]
  (let [fetch-headers #js {"Accept"       (c/get content-types accept)
                           "Content-Type" (c/get content-types content-type)}]
    (doseq [[k v] headers]
      (aset fetch-headers k v))
    #js {:method          (str/upper-case (name method))
         :headers         fetch-headers
         :redirect        (name redirect)
         :mode            (name mode)
         :cache           (name cache)
         :signal          signal
         :credentials     (name credentials)
         :referrer-policy (name referrer-policy)}))

(defn request [url & [{:keys [method accept content-type query-params body]
                       :as   opts
                       :or   {accept       :transit-json
                              content-type :transit-json}}]]
  (let [url     (-> url
                    uri/uri
                    (uri/assoc-query* query-params)
                    str)
        request-opts (let [r (fetch-opts opts)]
                  (when body
                    (aset r "body" (if (string? body)
                                      body
                                      (encode-body content-type body opts))))
                  (aset r "url" url)
                  r)]   
    (->
      (js/fetch url request-opts)
      (.then
        (fn [response]
          (let [headers (.-headers ^js response)
                header-map (into {} (map vec) (es6-iterator-seq (.entries ^js headers)))
                content-type-header (.get ^js headers "Content-Type")
                content-type (when content-type-header
                               (c/get (set/map-invert content-types)
                                 (str/replace content-type-header #";.*" "")))]
            (->
              (decode-body content-type response opts)
              (.then (fn [body]
                       ^{::request  request-opts
                         ::response response}
                       {:status  (.-status ^js response)
                        :headers header-map
                        :body    body}))
              (.catch (fn [e]
                        ^{::request  request-opts
                          ::response response}
                        {:error e})))))))
    ))

(def get request)

(defn post [url & [opts]]
  (request url (assoc opts :method :post)))

(defn put [url & [opts]]
  (request url (assoc opts :method :put)))

(defn delete [url & [opts]]
  (request url (assoc opts :method :delete)))

(defn head [url & [opts]]
  (request url (assoc opts :method :head)))


(comment
  (p/let [result (get "/as400/paginated/VSBSTAMDTA.STOVKP"
                      {:query-params {:page 1
                                      :page-size 20}})]
    (def xxx result))

  (p/let [body (:body xxx)]
    (def body body))

  (p/let [res (head "/as400/paginated/VSBSTAMDTA.STOVKP")]
    (def xxx res)))
