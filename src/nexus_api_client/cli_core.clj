(ns nexus-api-client.cli-core
  (:require
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [babashka.http-client :as http]
   [nexus-api-client.core :as c]))


(defn load-config!
  "Returns the content of config file as a clojure map datastructure"
  [^String config]
  (let [config-path (.getAbsolutePath (io/file config))]
    (try
      (read-string (slurp config-path))
      (catch java.io.FileNotFoundException e
        (binding [*out* *err*]
          (println "Missing config file" (.getMessage e)
                   "\nYou can use --config path_to_file to specify a path to config file"))))))

(defn doc
  "Returns essential information about the operation."
  [{:keys [v1]} op endpoint]
  (let [url endpoint
        path (some-> v1 op (:path op))]
    (some-> v1
            op
            (select-keys [:method :path :params :summary])
            (assoc :doc-url (str url path)))))

(defn api-request
  ([method url]
   (api-request method url nil))
  ([method url opts]
  ;;  (println method url opts)
   (try (http/request
         (merge {:method method :uri (str url)} opts))
        (catch java.lang.IllegalStateException _
          (println "Bad request: \n method: " method "\n uri: " url "\n opts: " opts)))))


(defn json->edn [s]
  (json/parse-string s true))

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
      (let [api (c/load-api)
            ops-opts (doc api op-name url)
            ops-params (:params ops-opts)
            request-params (reduce (partial c/gather-params supplied-params)
                                   {}
                                   ops-params)
            query-params (:query request-params)
            interpolate-path-opts (:path request-params)
            method (:method ops-opts)
            path (:path ops-opts)
            new-path (c/interpolate-path path interpolate-path-opts)
            invoke-url (if (empty? query-params)
                         (str url new-path)
                         (str url new-path "?" (c/create-query query-params)))
            response-body (:body (api-request method invoke-url {:basic-auth [user pass]}))]
        (json->edn response-body))
      (catch clojure.lang.ExceptionInfo e
        (let [data (ex-data ^Throwable e)
              body (:body data)]
          (println (.getMessage e)
                   "response-body: " body))))))