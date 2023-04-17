(ns nexus-api-client.interface
  (:require [nexus-api-client.core :as core]
            [clj-http.client :as c]
            [clojure.data.json :as json]))

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
        api (core/load-api)
        op-name (:operation invoke-opts)
        ops-opts (doc api op-name)
        supplied-params (:params invoke-opts)
        ops-params (:params ops-opts)
        request-params (reduce (partial core/gather-params supplied-params)
                               {}
                               ops-params)
        query-params (:query request-params)
        interpolate-path-opts (:path request-params)
        method (name (:method ops-opts))
        path (:path ops-opts)
        new-path (core/interpolate-path path interpolate-path-opts)
        client (if (empty? query-params)
                 (str url new-path)
                 (str url new-path "?" (core/create-query query-params)))
        to-call (str "c/" method " " client)]
    to-call
    ))


(comment
  (let [components-request (c/get "http://localhost:8081/service/rest/v1/components?repository=docker")
        components-body (:body components-request)
        items-map (json/read-str components-body :key-fn keyword)]
    items-map)

  (c/get "http://localhost:8081/service/rest/v1/components?repository=docker")

  (invoke {:endpoint "http://localhost:8081/service/rest"
           :creds {:user "admin" :pass "admin"}}
          {:operation :getRepository
           :params {:repositoryName "docker"}})
  
  

  (invoke {:endpoint "http://localhost:8081/service/rest"
           :creds {:user "admin" :pass "admin"}}
          {:operation :getAssetById
           :params {:id "bWF2ZW4tY2VudHJhbDozZjVjYWUwMTc2MDIzM2I2MjRiOTEwMmMwMmNiYmU4YQ"}})


  (invoke {:endpoint "http://localhost:8081/service/rest"
           :creds {:user "admin" :pass "admin"}}
          {:operation :getRole
           :params {:privilegeName "nass" :userId "admin" :source "default" :id "abraca-dabra"}})

  (ops (core/load-api))
  (doc (core/load-api) :getAssetById)

  (ops (core/load-api))
  (c/get "http://localhost:8081/service/rest/v1/repositories/docker")

  0
  )