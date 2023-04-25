(ns nexus-api-client.core
  (:require [clj-http.client :as c]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [jsonista.core :as json])
  (:import [java.io PushbackReader]
           [java.util.regex Pattern]))

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

(defn interpolate-path
  "Replaces all occurrences of {k1}, {k2} ... with the value map provided.
  Example:
  given a/path/{id}/on/{not-this}/root/{id} and {:id hello}
  results in: a/path/hello/{not-this}/root/hello."
  [path value-map]
  (let [[param value] (first value-map)]
    (if (nil? param)
      path
      (recur (str/replace path
                        (re-pattern (format "\\{([%s].*?)\\}"
                                            (-> param
                                                name
                                                Pattern/quote)))
                        (str value))
             (dissoc value-map param)))))

(defn bail-out
  [^String message]
  (throw (IllegalArgumentException. message)))

(defn load-api
  "Loads the API EDN file from resources."
  []
  (if-let [config "sonatype-nexus/api.edn"]
    (-> (io/resource config)
        (io/reader)
        (PushbackReader.)
        (edn/read))
    (bail-out "Cannot load api, the engine, version combo may not be supported.")))

(defn remove-internal-meta
  "Removes keywords namespaced with :contajners. They are for internal use."
  [data-seq]
  (remove #(= "contajners" (namespace %)) data-seq))

(defn create-query
  [m]
  (str/join "&" (map #(str (name (key %)) "=" (val %)) m)))

(defn api-request [method url & [opts]]
    (c/request
     (merge {:method method :url (str url)} opts)))

(defn json->edn [s]
  (json/read-value s (json/object-mapper {:decode-key-fn true})))

(comment

  (let [components-request (c/get "http://localhost:8081/service/rest/v1/components?repository=docker")
        components-body (:body components-request)
        items-map (json/read-str components-body :key-fn keyword)]
    items-map)
  (api-request :get "http://localhost:8081/service/rest" "/v1/components?repository=docker")

  (c/get "http://localhost:8081/service/rest/v1/components?repository=docker")

  (reduce (partial gather-params {:a 42 :b 64 :c 44})
          {}
          [{:name "a" :in :path}
           {:name "b" :in :query}
           {:name "c" :in :query}])

  (reduce (partial gather-params {:a 42 :b 64 :c 44})
          {}
          [{:name "a" :in :path}
           {:name "b" :in :query}
           {:name "c" :in :query}])

  (interpolate-path "/a/{w}/b/{x}/{y}" {:x 41 :y 42 :z 43})
  (interpolate-path "/{repoName}/" {:repoName "nas"})

  (json/read-value "{\n  \"name\" : \"docker\",\n  \"format\" : \"docker\",\n  \"type\" : \"hosted\",\n  \"url\" : \"http://localhost:8081/repository/docker\",\n  \"attributes\" : { }\n}" (json/object-mapper {:decode-key-fn true}))

  0
  )