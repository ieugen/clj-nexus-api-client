(ns nexus-api-client.interface
  (:require
   [clojure.data.json :as json]
   [nexus-api-client.jvm-runtime :as nrt]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as s]
   [nexus-api-client.interface :as int])
  (:import
   [java.io PushbackReader]
   [java.util.regex Pattern]))

(defn bail-out
  [^String message]
  (throw (IllegalArgumentException. message)))

(defn remove-internal-meta
  "Removes keywords namespaced with :contajners. They are for internal use."
  [data-seq]
  (remove #(= "contajners" (namespace %)) data-seq))

(defn load-api
  "Loads the API EDN file from resources."
  [version]
  (let [config "resources/sonatype-nexus/api.edn"]
    (-> config
        (io/reader)
        (PushbackReader.)
        (edn/read)
        (version))
    #_(bail-out "Cannot load api, the engine, version combo may not be supported.")))

(defn gather-params
  "Reducer fn categorizing the params as :header, :query or :path.
  supplied-params: map of params the user has passed when invoking.
  request-params: accumulator for all the params to be actually sent.
  each param in the spec is passed as the last arg and categorized by `in` if it is supplied."
  [supplied-params request-params {:keys [name in]}]
  (let [param (keyword name)]
    (if-not (contains? supplied-params param)
      request-params
      (update-in request-params [(keyword in)] assoc param (param supplied-params)))))

(defn maybe-serialize-body
  "If the body is a map, convert it to JSON and attach the correct headers."
  [{:keys [body] :as request}]
  (if (map? body)
    (-> request
        (assoc-in [:headers "content-type"] "application/json")
        (update :body json/write-str))
    request))

(defn interpolate-path
  "Replaces all occurrences of {k1}, {k2} ... with the value map provided.
  Example:
  given a/path/{id}/on/{not-this}/root/{id} and {:id hello}
  results in: a/path/hello/{not-this}/root/hello."
  [path value-map]
  (let [[param value] (first value-map)]
    (if (nil? param)
      path
      (recur (s/replace path
                        (re-pattern (format "\\{([%s].*?)\\}"
                                            (-> param
                                                name
                                                Pattern/quote)))
                        (str value))
             (dissoc value-map param)))))

(defn try-json-parse
  "Attempts to parse `value` as a JSON string. no-op if its not a valid JSON string."
  [value]
  (try
    (json/read-str value :key-fn keyword)
    (catch Exception _ value)))

(comment 
  (+ 2 2)

  (remove-internal-meta [:contajners/foo :foo])
  (try-json-parse "[1, 2, 3]")
  (try-json-parse "yesnt")

 (def client (nrt/client "tcp://localhost:8080" {}))

(nrt/request {:client client :url "/v1.40/_ping" :method :get})

(nrt/request {:client client :url "/containers/json" :method :get})

(reduce (partial gather-params {:a 42 :b 64 :c 44})
        {}
        [{:name "a" :in :path}
         {:name "b" :in :query}
         {:name "c" :in :query}])

(maybe-serialize-body {:body {:a 42}})

(maybe-serialize-body {:body 42})

(interpolate-path "/a/{w}/b/{x}/{y}" {:x 41 :y 42 :z 43})
(load-api :v1)
  (let [api (load-api :v1)]
    (->> api 
         (keys)
         (int/remove-internal-meta)))

  0)