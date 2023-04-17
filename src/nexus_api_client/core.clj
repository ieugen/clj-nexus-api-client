(ns nexus-api-client.core
  (:require [clojure.string :as str]
            [clojure.edn :as edn]
            [clojure.java.io :as io])
  (:import
   [java.util.regex Pattern]
   [java.io PushbackReader]))

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

(comment
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
  0
  )