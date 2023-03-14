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

#_(defn bail-out
  [^String message]
  (throw (IllegalArgumentException. message)))

#_(defn remove-internal-meta
  "Removes keywords namespaced with :contajners. They are for internal use."
  [data-seq]
  (remove #(= "contajners" (namespace %)) data-seq))

#_(defn load-api
  "Loads the API EDN file from resources."
  [version]
  (let [config "resources/sonatype-nexus/api.edn"]
    (-> config
        (io/reader)
        (PushbackReader.)
        (edn/read)
        (version))
    #_(bail-out "Cannot load api, the engine, version combo may not be supported.")))

#_(defn gather-params
  "Reducer fn categorizing the params as :header, :query or :path.
  supplied-params: map of params the user has passed when invoking.
  request-params: accumulator for all the params to be actually sent.
  each param in the spec is passed as the last arg and categorized by `in` if it is supplied."
  [supplied-params request-params {:keys [name in]}]
  (let [param (keyword name)]
    (if-not (contains? supplied-params param)
      request-params
      (update-in request-params [(keyword in)] assoc param (param supplied-params)))))

#_(defn maybe-serialize-body
  "If the body is a map, convert it to JSON and attach the correct headers."
  [{:keys [body] :as request}]
  (if (map? body)
    (-> request
        (assoc-in [:headers "content-type"] "application/json")
        (update :body json/write-str))
    request))

#_(defn interpolate-path
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

#_(defn try-json-parse
  "Attempts to parse `value` as a JSON string. no-op if its not a valid JSON string."
  [value]
  (try
    (json/read-str value :key-fn keyword)
    (catch Exception _ value)))

(defn json-to-edn
  "Converts a json file to edn format and writes to file"
  [json-f file-name]
  (let [f (slurp json-f)
        edn-f (json/read-str f :key-fn keyword)]
    (spit (str file-name) edn-f)))

(defn load-edn
  [source]
  (let [stream source]
    (-> stream
        (io/reader)
        (PushbackReader.)
        (edn/read))))

(defn get-image-versions
  [image-name]
  (let [data (load-edn "resources/sonatype-nexus/docker-components.edn")
        images (:items data)
        target-images (filter #(= (:name %) image-name) images)
        tags (map #(:version %) target-images)]
    (prn tags)))

(comment
  (+ 2 2)
  (get-image-versions "hello-world")
  (load-edn "resources/sonatype-nexus/docker-components.edn")
  (let [f (slurp "resources/sonatype-nexus/docker-components.json")]
    (json/read-str f  :key-fn keyword))
  (json-to-edn "resources/sonatype-nexus/docker-components.json" "test.edn")
  #_(remove-internal-meta [:contajners/foo :foo])
  #_(try-json-parse "[1, 2, 3]")
  #_(try-json-parse "yesnt")

  (def client (nrt/client "tcp://localhost:8080" {}))

  (nrt/request {:client client :url "/v1.40/_ping" :method :get})

  (nrt/request {:client client :url "/containers/json" :method :get})

  #_(reduce (partial gather-params {:a 42 :b 64 :c 44})
            {}
            [{:name "a" :in :path}
             {:name "b" :in :query}
             {:name "c" :in :query}])

  #_(maybe-serialize-body {:body {:a 42}})

  #_(maybe-serialize-body {:body 42})

  #_(interpolate-path "/a/{w}/b/{x}/{y}" {:x 41 :y 42 :z 43})
  #_(load-api :v1)
  #_(let [api (load-api :v1)]
      (->> api
           (keys)
           (int/remove-internal-meta)))

  0
  )