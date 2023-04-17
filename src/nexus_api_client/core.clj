(ns nexus-api-client.core
  (:require [nexus-api-client.interface :as interface]
            [clojure.string :as str]))

(defn ops
  "Returns the supported operations for sonatype nexus (v1) API."
  [{:keys [v1]}]
  (->> v1
       (keys)
       (interface/remove-internal-meta)))

(defn doc
  "Returns essential information about the operation."
  [{:keys [v1]} op]
  (let [url "http://localhost:8081/service/rest"
        path (some-> v1 op (:path op))]
    (some-> v1
            op
            (select-keys [:method :path :params :summary])
            (assoc :doc-url (str url path)))))

(defn create-query
  [m]
  (str/join "&" (map #(str (name (key %)) "=" (val %)) m)))

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
        api (interface/load-api)
        op-name (:operation invoke-opts)
        ops-opts (doc api op-name)
        supplied-params (:params invoke-opts)
        ops-params (:params ops-opts)
        request-params (reduce (partial interface/gather-params supplied-params)
                               {}
                               ops-params)
        query-params (:query request-params)
        interpolate-path-opts (:path request-params)
        method (str/upper-case (name (:method ops-opts)))
        path (:path ops-opts)
        new-path (interface/interpolate-path path interpolate-path-opts)]
    (if (empty? query-params) 
      (str "curl -u " user ":" pass " -X " method " " url new-path)
      (str "curl -u " user ":" pass " -X " method " " url new-path "?" (create-query query-params)))))


(comment

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

  (ops (interface/load-api))
  (doc (interface/load-api) :getAssetById)

  (ops (interface/load-api))

  0
  )