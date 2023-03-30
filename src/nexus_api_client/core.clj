(ns nexus-api-client.core
  (:require
   [nexus-api-client.jvm-runtime :as rt]
   [nexus-api-client.interface :as interface]
   [clojure.edn :as edn]
   [clojure.java.io :as io])
  (:import
   [java.io PushbackReader]
   [java.util.regex Pattern]))

#_(defn categories
  "Returns the available categories for an engine at a specified verison.
  Categories are the kind of operations the engine can do.
  eg. :docker and v1.41
      :podman and v3.2.3"
  [engine version]
  (->> (interface/load-api) ;;TODO: add params
       (keys)
       (interface/remove-internal-meta)))

(defn client
  "Creates a client scoped to an engine, category, connection settings and API version.
  Connection settings:
  uri: The full URI with the protocol for the connection to the engine.
  read-timeout: Read timeout in ms.
  write-timeout: Write timeout in ms.
  call-timeout: Total round trip timeout in ms.
  mtls: A map having the paths to the CA, key and cert to perform Mutual TLS with the engine."
  [{:keys [engine category conn version]}]
  (let [api (interface/load-api) ;;TODO: add params
        {:keys [uri
                connect-timeout
                read-timeout
                write-timeout
                call-timeout
                mtls]}
        conn]
    {:category category
     :api (-> api
              category
              (merge (select-keys api [:contajners/doc-url])))
     :conn (rt/client uri
                      {:connect-timeout-ms connect-timeout
                       :read-timeout-ms read-timeout
                       :write-timeout-ms write-timeout
                       :call-timeout-ms call-timeout
                       :mtls mtls})
     :version version}))

(defn ops
  "Returns the supported operations for a client."
  [{:keys [v1]}]
  (->> v1
       (keys)
       (interface/remove-internal-meta)))

(defn doc
  "Returns the summary and doc URL of the operation in the client."
  [{:keys [v1]} op]
  (let [url "http://localhost:8081/service/rest"
        path (some-> v1 op (:path op))]
    (println path)
    (some-> v1
            op
            (select-keys [:summary])
            (assoc :doc-url (str url path)))))


(defn invoke
  "Performs the operation with the specified client and a map of options.
  Options map:
  op: The operation to invoke on the engine. Required.
  params: The params needed for the operation. Default: {}.
  data: The payload needed to be sent to the op. Maps will be JSON serialized. Corresponds to the Request Body in docs. Default: {}.
  as: The return type of the response. :data, :stream, :socket. Default: :data.
  throw-exceptions: Throws exceptions when status is >= 400 for API calls. Default: false.
  throw-entire-message: Includes the full exception as a string. Default: false."
  [{:keys [version conn api category]} {:keys [op params data as throw-exceptions throw-entire-message]}]
  (if-let [operation (op api)]
    (let [request-params (reduce (partial interface/gather-params params)
                                 {}
                                 (:params operation))
          request {:client conn
                   :method (:method operation)
                   :path (-> operation
                             :path
                             (interface/interpolate-path (:path request-params))
                             (as-> path (str "/" version path)))
                   :headers (:header request-params)
                   :query-params (:query request-params)
                   :body data
                   :as as
                   :throw-exceptions throw-exceptions
                   :throw-entire-message throw-entire-message}
          response (-> request
                       (interface/maybe-serialize-body)`
                       (rt/request))]
      (case as
        (:socket :stream) response
        (interface/try-json-parse response)))
    (interface/bail-out (format "Invalid operation %s for category %s"
                           (name op)
                           (name category)))))

(comment
  (+ 2 2)
  #_(categories nil nil)
   (def d-client
     (client {:engine :docker
              :version "v1.41"
              :category :containers
              :conn {:uri "unix:///var/run/docker.sock"}}))
   (def d-images
     (client {:engine :docker
              :version "v1.41"
              :category :images
              :conn {:uri "unix:///var/run/docker.sock"}}))
  
  (ops d-client)
  (doc d-images :ImageCreate)



  (ops (interface/load-api))
  (doc (interface/load-api) :update_1)


  (def images-docker (client {:engine   :docker
                              :category :images
                              :version  "v1.41"
                              :conn     {:uri "unix:///var/run/docker.sock"}}))

  (ops (interface/load-api))
  (let [m {:name "nassss/aba"}]
    (:name m))

  0
  )