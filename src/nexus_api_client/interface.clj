(ns nexus-api-client.interface
  (:require [nexus-api-client.core :as core]))

(defn ops
  "Returns the supported operations for sonatype nexus (v1) API."
  [{:keys [v1]}]
  (->> v1
       (keys)
       (core/remove-internal-meta)))

(defn doc
  "Returns essential information about the operation."
  [{:keys [v1]} op]
  (let [url "http://localhost:8081/service/rest"
        path (some-> v1 op (:path op))]
    (some-> v1
            op
            (select-keys [:method :path :params :summary])
            (assoc :doc-url (str url path)))))

(defn invoke
  "Generates a string representing a curl command which can be run to perform the operation with the specified client and a set of params.

  Connection ptions map:
  endpoint: nexus api-url
  creds: a map containing the keys :user and :pass needed to access the endpoint
  operation: The operation to invoke. Required.
  params: The params needed for the operation. Default: {}."

  [conn-opts invoke-opts]

  (let [url (:endpoint conn-opts)
        user (-> conn-opts :creds :user)
        pass (-> conn-opts :creds :pass)

        op-name (:operation invoke-opts)

        supplied-params (:params invoke-opts)]
    (try
      (let [api (core/load-api)
            ops-opts (doc api op-name)
            ops-params (:params ops-opts)
            request-params (reduce (partial core/gather-params supplied-params)
                                   {}
                                   ops-params)
            query-params (:query request-params)
            interpolate-path-opts (:path request-params)
            method (name (:method ops-opts))
            path (:path ops-opts)
            new-path (core/interpolate-path path interpolate-path-opts)
            invoke-url (if (empty? query-params)
                         (str url new-path)
                         (str url new-path "?" (core/create-query query-params)))
            response-body (:body (core/api-request method invoke-url {:basic-auth [user pass]}))]
        (core/json->edn response-body))
      (catch Exception e
        (throw e)))))


(comment
  (invoke {:endpoint "http://localhost:8081/service/rest"
           :creds {:user "admin" :pass "admin"}}
          {:operatio :getRepository
           :params {:repositoryName "docker"}})

  (invoke {:endpoint "http://localhost:8081/service/res"
           :creds {:user "admin" :pass "admin"}}
          {:operation :getAssetById
           :params {:id "bWF2ZW4tY2VudHJhbDozZjVjYWUwMTc2MDIzM2I2MjRiOTEwMmMwMmNiYmU4YQ"}})

  (try
    (throw
     (ex-info "The ice cream has melted!"
              {:causes             #{:fridge-door-open :dangerously-high-temperature}
               :current-temperature {:value 25 :unit :celsius}}))
    (catch Exception e (ex-data e)))


  (invoke {:endpoint "http://localhost:8081/service/rest"
           :creds {:user "admin" :pass "admin"}}
          {:operation :getRole
           :params {:privilegeName "nass" :userId "admin" :source "default" :id "abraca-dabra"}})

  (def api (core/load-api))
  (doc api :deleteComponent)
  ;; => {:method :delete, :path "/v1/components/{id}", :params ({:name "id", :in :path}), :summary "Delete a single component", :doc-url "http://localhost:8081/service/rest/v1/components/{id}"}

  (doc api :getComponents)
  ;; => {:method :get, :path "/v1/components", :params ({:name "continuationToken", :in :query} {:name "repository", :in :query}), :summary "List components", :doc-url "http://localhost:8081/service/rest/v1/components"}

  (doc api :search)
  ;; => {:method :get, :path "/v1/search", :params ({:name "continuationToken", :in :query} {:name "sort", :in :query} {:name "direction", :in :query} {:name "timeout", :in :query} {:name "q", :in :query} {:name "repository", :in :query} {:name "format", :in :query} {:name "group", :in :query} {:name "name", :in :query} {:name "version", :in :query} {:name "prerelease", :in :query} {:name "md5", :in :query} {:name "sha1", :in :query} {:name "sha256", :in :query} {:name "sha512", :in :query} {:name "conan.baseVersion", :in :query} {:name "conan.channel", :in :query} {:name "conan.revision", :in :query} {:name "conan.packageId", :in :query} {:name "conan.packageRevision", :in :query} {:name "docker.imageName", :in :query} {:name "docker.imageTag", :in :query} {:name "docker.layerId", :in :query} {:name "docker.contentDigest", :in :query} {:name "maven.groupId", :in :query} {:name "maven.artifactId", :in :query} {:name "maven.baseVersion", :in :query} {:name "maven.extension", :in :query} {:name "maven.classifier", :in :query} {:name "gavec", :in :query} {:name "npm.scope", :in :query} {:name "npm.author", :in :query} {:name "npm.description", :in :query} {:name "npm.keywords", :in :query} {:name "npm.license", :in :query} {:name "npm.tagged_is", :in :query} {:name "npm.tagged_not", :in :query} {:name "nuget.id", :in :query} {:name "nuget.tags", :in :query} {:name "nuget.title", :in :query} {:name "nuget.authors", :in :query} {:name "nuget.description", :in :query} {:name "nuget.summary", :in :query} {:name "p2.pluginName", :in :query} {:name "pypi.classifiers", :in :query} {:name "pypi.description", :in :query} {:name "pypi.keywords", :in :query} {:name "pypi.summary", :in :query} {:name "rubygems.description", :in :query} {:name "rubygems.platform", :in :query} {:name "rubygems.summary", :in :query} {:name "yum.architecture", :in :query} {:name "yum.name", :in :query}), :summary "Search components", :doc-url "http://localhost:8081/service/rest/v1/search"}



  (doc api :getComponentById)
 ;; => {:method :get, :path "/v1/components/{id}", :params ({:name "id", :in :path}), :summary "Get a single component", :doc-url "http://localhost:8081/service/rest/v1/components/{id}"}

  (ops (core/load-api))

  (invoke conn
          {:operation :getComponents
           :params {:repository "dre-registry"}})

  (let [data  (invoke conn
                      {:operation :search
                       :params {:repository "dre-registry"
                                :format "docker"
                                :name "docsearch/old-ui"
                                :version "*"}})
        items (:items data)]
    (doseq [{:keys [id name version]} items]
      (println "Removing:" id name version "->"
               #_(invoke conn {:operation :deleteComponent
                               :params {:id id}}))))

  (invoke conn
          {:operation :getComponentById
           :params {:id "ZHJlLXJlZ2lzdHJ5OjEzYjI5ZTQ0OWYwZTNiOGRkNTM4OWI0NzU5N2I0ODU2"}})

  (invoke conn
          {:operation :deleteComponent
           :params {:id "ZHJlLXJlZ2lzdHJ5OjEzYjI5ZTQ0OWYwZTNiOGRkNTM4OWI0NzU5N2I0ODU2"}})


  (let [api (core/load-api)
        ops (ops api)
        data (map #(doc api %) ops)]
    #_(println data)
    (doseq [op ops]
      (let [d (doc api op)]
        (spit "nexus-api.txt" (prn-str {op (:summary d)}) :append true))))

  {:operation :deleteComponent
   :params {:id ""}}



  (doc (core/load-api) :getAssetById)



  (ops (core/load-api))

  0
  )