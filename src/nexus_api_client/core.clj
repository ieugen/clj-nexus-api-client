(ns nexus-api-client.core
  (:require
   [nexus-api-client.jvm-runtime :as rt]
   [nexus-api-client.interface :as interface]))

#_(defn categories
  "Returns the available categories for an engine at a specified verison.
  Categories are the kind of operations the engine can do.
  eg. :docker and v1.41
      :podman and v3.2.3"
  [engine version]
  (->> (interface/load-api :v1) ;;TODO: add params
       (keys)
       (interface/remove-internal-meta)))

#_(defn client
  "Creates a client scoped to an engine, category, connection settings and API version.
  Connection settings:
  uri: The full URI with the protocol for the connection to the engine.
  read-timeout: Read timeout in ms.
  write-timeout: Write timeout in ms.
  call-timeout: Total round trip timeout in ms.
  mtls: A map having the paths to the CA, key and cert to perform Mutual TLS with the engine."
  [{:keys [engine category conn version]}]
  (let [api (interface/load-api :v1) ;;TODO: add params
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

#_(defn ops
  "Returns the supported operations for a client."
  [{:keys [api]}]
  (->> api
       (keys)
       (interface/remove-internal-meta)))

#_(defn doc
  "Returns the summary and doc URL of the operation in the client."
  [{:keys [version api]} op]
  (some-> api
          op
          (select-keys [:summary])
          (assoc :doc-url
                 (format (:contajners/doc-url api)
                         version
                         (name op)))))

#_(defn invoke
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
                       (interface/maybe-serialize-body)
                       (rt/request))]
      (case as
        (:socket :stream) response
        (interface/try-json-parse response)))
    (interface/bail-out (format "Invalid operation %s for category %s"
                           (name op)
                           (name category)))))

(comment 
  (+ 2 2)
  0)