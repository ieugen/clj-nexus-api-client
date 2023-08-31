(ns main
  "Heavily borrowed from Rahuλ Dé - lispyclouds
   https://github.com/lispyclouds/contajners/blob/main/fetch_api/main.clj"
  (:require [babashka.http-client :as http]
            [clojure.string :as s])
  (:import [io.swagger.parser OpenAPIParser]
           [io.swagger.v3.oas.models Operation PathItem]
           [io.swagger.v3.oas.models.parameters Parameter]
           [io.swagger.v3.parser.core.models ParseOptions]))

(def resource-path "resources/sonatype-nexus")

(defn fetch-spec
  "Download the spec from the URL and version provided."
  [url]
  (let [{:keys [status body]} (http/get url)]
    (if (>= status 400)
      (binding [*out* *err*]
        (println
         (format "Error fetching API %s" body)))
      body)))


(defn find-first
  [pred coll]
  (some #(when (pred %) %)
        coll))

;; TODO: Better?
(defn ->category
  "Given a path and a set of namespaces, returns the (namespaced)category.
  path: /containers/json
  namespaces: #{}
  category: :containers
  path: /libpod/containers/json
  namespaces: #{\"/libpod\"}
  category: :libpod/containers
  path: /libpod/deeper/api/containers/json
  namespaces: #{\"/libpod/deeper/api\"}
  category: :libpod.deeper.api/containers
  The category is the prefix of the path being passed. eg /containers, /images
  The set of namespaces, if passed, determines if the category is to be namespaced. eg /libpod/containers and /containers
  The namespace is useful to distinguish similarly named ops in APIs supporting compatibility with other engines."
  [path namespaces]
  (let [matched (find-first #(s/starts-with? path %) namespaces)
        nspace (when matched
                 (-> matched
                     (subs 1)
                     (s/replace "/" ".")))
        path (if matched
               (s/replace-first path matched "")
               path)
        category (-> path
                     (subs 1)
                     (s/split #"/")
                     (first))]
    (tap> {:name "->category"
           :path path
           :namespaces namespaces
           :category category})
    (if nspace
      (keyword nspace category)
      (keyword category))))

;; TODO: Parse and validate the types someday
(defn ->params
  "Given a io.swagger.v3.oas.models.parameters.Parameter, returns a map of necessary keys."
  [^Parameter param]
  {:name (.getName param)
   :in (keyword (.getIn param))})

(defn ->operation
  "Given a path, http method and an io.swagger.v3.oas.models.Operation, returns a map of operation id and necessary keys."
  [path method ^Operation operation]
  (let [op {:summary (.getSummary operation)
            :method (-> method
                        str
                        s/lower-case
                        keyword)
            :path path
            :params (map ->params (.getParameters operation))}
        request-body (.getRequestBody operation)]
    (tap> {:name "->operation"
           :path path
           :method method
           :operation operation
           :op op})
    {(keyword (.getOperationId operation)) (if request-body
                                             (assoc op :request-body true)
                                             op)}))

(defn ->operations
  "Given a set of namespaces, path and a io.swagger.v3.oas.models.PathItem returns a list of maps of operations."
  [namespaces path ^PathItem path-item]
  (let [ops (->> (.readOperationsMap path-item)
             (map #(->operation path (key %) (val %)))
             (map #(hash-map (->category path namespaces) %)))]
    #_(tap> {:name "->operations"
           :namespace namespaces
           :path path
           :path-item path-item
           :ops ops})
    ops))


(defn parse
  "Given a set of namespaces and the OpenAPI 2.0 spec as a string, returns the spec in the following format:
  {:category1 {:operation-id1 {:summary \"summary\"
                               :method  :HTTP_METHOD
                               :path    \"/path/to/api\"
                               :params  [{:name \"..\"}]}}
   :namespaced/category {...}}"
  [^String spec namespaces]
  (let [parse-options (doto (ParseOptions.)
                        (.setResolveFully true))]
    (->> (.readContents (OpenAPIParser.) spec nil parse-options) ; specs are still Swagger 2.0 🙄
         (.getOpenAPI)
         (.getPaths)
         (mapcat #(->operations namespaces (key %) (val %)))
         (apply (partial merge-with into)))))



(comment

  (spit "resources/sonatype-nexus/api.json"
        (fetch-spec "https://localhost:8080/service/rest/swagger.json"))

  (spit "resources/sonatype-nexus/api.edn"
        (-> (fetch-spec "https://localhost:8080/service/rest/swagger.json")
            (parse #{})))
  )