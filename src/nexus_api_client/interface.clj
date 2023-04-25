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
            response-body (:body (core/api-request method invoke-url))]
        (core/json->edn response-body))
      (catch Exception e
        (prn (let [api (core/load-api)
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
                                (str url new-path "?" (core/create-query query-params)))]
               (str "caught exception: \n" (.getMessage e)
                    "\n Request: " invoke-url
                    "\n Response: " (core/api-request method invoke-url))))))))





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
    (map "fst")
    (throw
     (ex-info "The ice cream has melted!"
              {:causes             #{:fridge-door-open :dangerously-high-temperature}
               :current-temperature {:value 25 :unit :celsius}}))
    (catch Exception e (ex-data e)))


  (invoke {:endpoint "http://localhost:8081/service/rest"
           :creds {:user "admin" :pass "admin"}}
          {:operation :getRole
           :params {:privilegeName "nass" :userId "admin" :source "default" :id "abraca-dabra"}})

  (ops (core/load-api))
  (doc (core/load-api) :getAssetById)

  (ops (core/load-api))

  0)