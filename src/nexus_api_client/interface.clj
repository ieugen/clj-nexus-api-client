(ns nexus-api-client.interface
  (:require
   [clj-http.client :as c]
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
  (if-let [config "resources/sonatype-nexus/api.edn"]
    (-> config
        (io/reader)
        (PushbackReader.)
        (edn/read)
        (version))
    (bail-out "Cannot load api, the engine, version combo may not be supported.")))

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

;;;;;;;;;;;;;;;;;

#_(defn json-to-edn
  "Converts a json file to edn format and writes to file"
  [json-f file-name]
  (let [f (slurp json-f)
        edn-f (json/read-str f :key-fn keyword)]
    (spit (str file-name) edn-f)))


#_(defn load-edn
  [source]
  (let [stream source]
    (-> stream
        (io/reader)
        (PushbackReader.)
        (edn/read))))

(defn get-assets-list [repo]
  (let [url (str "http://localhost:8081/service/rest/v1/components?repository=" repo)
        req (c/get url)
        body (:body req)
        assets (json/read-str body :key-fn keyword)]
    (:items assets)))

(defn get-image-versions
  [args]
  (println (:name args))
  (println (:repo args))
  (let [assets (get-assets-list (:repo args))
        img-name (:name args)
        target-images (filter #(= (:name %) img-name) assets)
        tags (map #(:version %) target-images)
        t (into [] tags)]
    (println t)))

(comment
  (let [components-request (c/get "http://localhost:8081/service/rest/v1/components?repository=docker")
        components-body (:body components-request)
        items-map (json/read-str components-body :key-fn keyword)]
    items-map)

  (let [f (slurp "resources/sonatype-nexus/docker-components.json")]
    (json/read-str f  :key-fn keyword))
  #_(json-to-edn "resources/sonatype-nexus/docker-components.json")
  #_(remove-internal-meta [:contajners/foo :foo])
  #_(try-json-parse "[1, 2, 3]")
  #_(try-json-parse "yesnt")

  (def client (nrt/client "tcp://localhost:8080" {}))
  (def client (nrt/client "http://localhost:8081"))
   (http/get client "/_ping")

  (nrt/request {:client client :url "/v1.40/_ping" :method :get})
  (nrt/request {:client client :url "/service/rest/v1/components?repository=docker" :method :get})

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
  
  (let [m [{:id "ZG9ja2VyOjA5OTViYmFlZWIxZmJjM2VjYzEzODJkMjkwMzM2NzA1", :repository "docker", :format "docker", :group nil, :name "hello-world", :version "mytag2", :assets [{:path "v2/hello-world/manifests/mytag2", :fileSize 1988, :format "docker", :repository "docker", :uploader "admin", :blobCreated "2023-03-06T12:59:33.726+00:00", :lastModified "2023-03-06T12:59:33.726+00:00", :checksum {:sha1 "282b53ff1c1e722dfcb6f2ce382fd3cda3f463bb", :sha256 "cbf8f2e12add9f00fd416c4851f6ab4488f76620cb24fa604f27b4bfd1639b88"}, :id "ZG9ja2VyOmUxYTZiOGMzOGQzMWZhY2EzY2IxZjYwYzZhYTM5MzQ3", :contentType "application/vnd.docker.distribution.manifest.v2+json", :downloadUrl "http://localhost:8081/repository/docker/v2/hello-world/manifests/mytag2", :lastDownloaded nil, :uploaderIp "172.18.0.1"}]} {:id "ZG9ja2VyOmRjMDg4NmQ4MTk4MjI1M2YzODU0N2RkYjI2NTg4MGU0", :repository "docker", :format "docker", :group nil, :name "hello-world", :version "mytag3", :assets [{:path "v2/hello-world/manifests/mytag3", :fileSize 1988, :format "docker", :repository "docker", :uploader "admin", :blobCreated "2023-03-06T12:59:44.071+00:00", :lastModified "2023-03-06T12:59:44.071+00:00", :checksum {:sha1 "282b53ff1c1e722dfcb6f2ce382fd3cda3f463bb", :sha256 "cbf8f2e12add9f00fd416c4851f6ab4488f76620cb24fa604f27b4bfd1639b88"}, :id "ZG9ja2VyOjc0Y2RiZmNhNGRiOTdkNjQ1MDhkZTIzNGJmNjhhMTE4", :contentType "application/vnd.docker.distribution.manifest.v2+json", :downloadUrl "http://localhost:8081/repository/docker/v2/hello-world/manifests/mytag3", :lastDownloaded nil, :uploaderIp "172.18.0.1"}]} {:id "ZG9ja2VyOjhhZGE0M2ZjYTIxOWY1M2U2ZWU0ODczMDc2NWY4Zjg1", :repository "docker", :format "docker", :group nil, :name "salut-lume", :version "tag1", :assets [{:path "v2/salut-lume/manifests/tag1", :fileSize 1988, :format "docker", :repository "docker", :uploader "admin", :blobCreated "2023-03-06T13:00:57.370+00:00", :lastModified "2023-03-06T13:00:57.370+00:00", :checksum {:sha1 "282b53ff1c1e722dfcb6f2ce382fd3cda3f463bb", :sha256 "cbf8f2e12add9f00fd416c4851f6ab4488f76620cb24fa604f27b4bfd1639b88"}, :id "ZG9ja2VyOjYyNTRhNjg1YWM5Mzc4N2Y5ZWRmYzA4ZGNhMWJlZTdk", :contentType "application/vnd.docker.distribution.manifest.v2+json", :downloadUrl "http://localhost:8081/repository/docker/v2/salut-lume/manifests/tag1", :lastDownloaded nil, :uploaderIp "172.18.0.1"}]} {:id "ZG9ja2VyOjZiMDc0ZGMzMzRlYjJiYzkwYzgxODMyZTk4MGMwNDQw", :repository "docker", :format "docker", :group nil, :name "salut-lume", :version "ta2", :assets [{:path "v2/salut-lume/manifests/ta2", :fileSize 1988, :format "docker", :repository "docker", :uploader "admin", :blobCreated "2023-03-06T13:01:07.248+00:00", :lastModified "2023-03-06T13:01:07.248+00:00", :checksum {:sha1 "282b53ff1c1e722dfcb6f2ce382fd3cda3f463bb", :sha256 "cbf8f2e12add9f00fd416c4851f6ab4488f76620cb24fa604f27b4bfd1639b88"}, :id "ZG9ja2VyOmNkYWYzMTA0ZWNjN2NlM2FlODRlM2U5YTBlNGEzOTY2", :contentType "application/vnd.docker.distribution.manifest.v2+json", :downloadUrl "http://localhost:8081/repository/docker/v2/salut-lume/manifests/ta2", :lastDownloaded nil, :uploaderIp "172.18.0.1"}]} {:id "ZG9ja2VyOmE0YjlhZGM3MWEyMmQ4NDg4NTE3MzMwOTA2ZjYxMmQy", :repository "docker", :format "docker", :group nil, :name "zdrasthvooy-meer", :version "tagof1", :assets [{:path "v2/zdrasthvooy-meer/manifests/tagof1", :fileSize 1988, :format "docker", :repository "docker", :uploader "admin", :blobCreated "2023-03-06T13:04:32.395+00:00", :lastModified "2023-03-06T13:04:32.395+00:00", :checksum {:sha1 "282b53ff1c1e722dfcb6f2ce382fd3cda3f463bb", :sha256 "cbf8f2e12add9f00fd416c4851f6ab4488f76620cb24fa604f27b4bfd1639b88"}, :id "ZG9ja2VyOjFlODNiMmFjZWYyMDVlODRjN2EzYjMwNjc0ODE1Mjdj", :contentType "application/vnd.docker.distribution.manifest.v2+json", :downloadUrl "http://localhost:8081/repository/docker/v2/zdrasthvooy-meer/manifests/tagof1", :lastDownloaded nil, :uploaderIp "172.18.0.1"}]} {:id "ZG9ja2VyOmQxM2I3NGRlODJkNmRkMGFmMGM0YmUwMTZkZmE2NmJj", :repository "docker", :format "docker", :group nil, :name "zdrasthvooy-meer", :version "tagof2", :assets [{:path "v2/zdrasthvooy-meer/manifests/tagof2", :fileSize 1988, :format "docker", :repository "docker", :uploader "admin", :blobCreated "2023-03-06T13:04:34.137+00:00", :lastModified "2023-03-06T13:04:34.137+00:00", :checksum {:sha1 "282b53ff1c1e722dfcb6f2ce382fd3cda3f463bb", :sha256 "cbf8f2e12add9f00fd416c4851f6ab4488f76620cb24fa604f27b4bfd1639b88"}, :id "ZG9ja2VyOjI4MmRhMDFiNTkyYzA1NzQzMGNmMGNiZTMzZjI1OGRk", :contentType "application/vnd.docker.distribution.manifest.v2+json", :downloadUrl "http://localhost:8081/repository/docker/v2/zdrasthvooy-meer/manifests/tagof2", :lastDownloaded nil, :uploaderIp "172.18.0.1"}]}]
         target-images (filter #(= (:name %) "salut-lume") m)
          tags (map #(:version %) target-images)]
    tags)
  
  0
  )