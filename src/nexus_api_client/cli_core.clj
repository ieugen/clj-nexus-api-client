(ns nexus-api-client.cli-core
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [babashka.http-client :as http])
  (:import [java.io PushbackReader]
           [java.util.regex Pattern]
           [java.io IOException]))

(defn deep-merge
  "From https://clojuredocs.org/clojure.core/merge ."
  [a & maps]
  (if (map? a)
    (apply merge-with deep-merge a maps)
    (apply merge-with deep-merge maps)))

(defn println-err
  [& more]
  (binding [*out* *err*]
    (apply println more)))

(defn env->config!
  "Try to load nexus configuration options from environment.
   We produce a configuration that can be merged with other configs.
   Missing values are ok as configuring via env is optional.
   Looks and processes the following env vars:
   - NEXUS_CONFIG - read string as edn and return config.
     Do not process any other nexus env var.
   - NEXUS_ENDPOINT - string
   - NEXUS_USER - string
   - NEXUS_PASSWORD - read string as edn
   Return: A map representing the nexus configuration."
  ([]
   (env->config! (System/getenv)))
  ([env]
   (let [config (get env "NEXUS_CONFIG")]
     (if config
       (edn/read-string config)
       ;; we don't have NEXUS_CONFIG - check the other vars
       (let [endpoint (get env "NEXUS_ENDPOINT")
             user (get env "NEXUS_USER")
             password (get env "NEXUS_PASSWORD")]
         (cond-> {}
           endpoint (assoc :endpoint (keyword endpoint))
           user (assoc :user user)
           password (assoc :pass password)))))))

(defn cli-args->config
  "Parse any nexus configuration options from cli args.
   Return a nexus configuration map with any values.
   We expect the args we receive to be values
   processed by tools.cli parse-opts fn."
  [config-edn-str]
  (let [config (edn/read-string config-edn-str)]
    (if (map? config)
      config
      {})))

(defn file->config!
  "Read config-file as an edn.
   If config-file is nil, return nil.
   On IO exception print warning and return nil."
  [^String config-file]
  (when config-file
    (let [config-path (.getAbsolutePath (io/file config-file))]
      (try
        (edn/read-string (slurp config-path))
        (catch IOException e
          (println-err
           "WARN: Error reading config" (.getMessage e)
           "\nYou can use --config path_to_file to specify a path to config file"))))))

(defn load-config!
  "Load configuration and merge options.
   Options are loaded in this order.
   Subsequent values are deepmerged and replace previous ones.
   - configuration file - if it exists and we can parse it as edn
   - environment variables
   - command line arguments passed to the application
   Return a migratus configuration map."
  [config-file config-data]
  (let [config (file->config! config-file)
        env (env->config!)
        args (cli-args->config config-data)]
    (deep-merge config env args)))

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

(defn doc
  "Returns essential information about the operation."
  [{:keys [v1]} op endpoint]
  (let [url endpoint
        path (some-> v1 op (:path op))]
    (some-> v1
            op
            (select-keys [:method :path :params :summary])
            (assoc :doc-url (str url path)))))

(defn create-query
  [m]
  (str/join "&" (map #(str (name (key %)) "=" (val %)) m)))

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

(defn api-request
  ([method url]
   (api-request method url nil))
  ([method url opts]
  ;;  (println method url opts)
   (try (http/request
         (merge {:method method :uri (str url)} opts))
        (catch java.lang.Exception _
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
      (let [api (load-api)
            ops-opts (doc api op-name url)
            ops-params (:params ops-opts)
            request-params (reduce (partial gather-params supplied-params)
                                   {}
                                   ops-params)
            query-params (:query request-params)
            interpolate-path-opts (:path request-params)
            method (:method ops-opts)
            path (:path ops-opts)
            new-path (interpolate-path path interpolate-path-opts)
            invoke-url (if (empty? query-params)
                         (str url new-path)
                         (str url new-path "?" (create-query query-params)))
            response-body (:body (api-request method invoke-url {:basic-auth [user pass]}))]
        (json->edn response-body))
      (catch clojure.lang.ExceptionInfo e
        (let [data (ex-data ^Throwable e)
              body (:body data)]
          (println (.getMessage e)
                   "response-body: " body))))))