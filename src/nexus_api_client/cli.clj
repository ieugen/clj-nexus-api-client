(ns nexus-api-client.cli
  (:require [clojure.tools.cli :refer [parse-opts]]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [cheshire.core :as json]
            [babashka.http-client :as http])
  (:import [java.io PushbackReader]
           [java.util.regex Pattern]
           [java.time.format DateTimeFormatter]))

(defn remove-internal-meta
  "Removes keywords namespaced with :contajners. They are for internal use."
  [data-seq]
  (remove #(= "contajners" (namespace %)) data-seq))

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

(defn ops
  "Returns the supported operations for sonatype nexus (v1) API."
  [{:keys [v1]}]
  (->> v1
       (keys)
       (remove-internal-meta)))

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
        (catch java.lang.IllegalStateException e (println "Bad request: \n method: " method "\n uri: " url "\n opts: " opts)))))


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
            ops-opts (doc api op-name)
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

(defn usage [options-summary]
  (println (->> ["Usage: list [params]"
             ""
             "Parameters:"
             "  --repository <repository-name>"
             "  --image <image-name>"
             "Options:"
             options-summary]
            (str/join \newline))))

(defn error-msg [errors]
  (binding [*out* *err*]
    (println "The following errors occurred while parsing your command:\n\n"
             (str/join  \newline errors))))

(def global-cli-options
  [[nil "--config NAME" "Configuration file name" :default "config.edn"]
   ["-l" "--list" "list the repositories from nexus"]
   ["-r" "--repository NAME" "list the images for a specific repository"]
   ["-i" "--image NAME" "list the tags for a specific image"]
   [nil "--doc OP-NAME" "document named api operation"]
   ["-v" nil "Verbosity level; may be specified multiple times to increase value"
    :id :verbosity
    :default 0
    :update-fn inc]
   ["-h" "--help"]])

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

(defn list-repos [cfg]
  (let [{:keys [endpoint user pass]} cfg
        conn {:endpoint endpoint
              :creds {:user user :pass pass}}
        opts {:operation :getRepositories_1}
        repos (invoke conn opts)]
    (println "Nexus docker repositories: ")
    (println (apply str (repeat 25 "-")))
    (doseq [{:keys [name]} repos] (println name))))

(defn list-images [repo-name cfg]
  (let [{:keys [endpoint user pass]} cfg
        conn {:endpoint endpoint
              :creds {:user user :pass pass}}
        opts {:operation :getComponents
              :params {:repository repo-name}}
        components (atom [])
        continuation-token (atom nil)]
    (loop []
      (let [response (invoke conn (assoc-in opts [:params :continuationToken] @continuation-token))
            items (:items response)
            new-token (:continuationToken response)
            new-components (concat @components items)]
        (reset! components new-components)
        (reset! continuation-token new-token)
        #_(reset! continuation-token new-token)
        (when new-token
          #_(println "Nexus docker images for " repo-name " repository:")
          #_(println (apply str (repeat 35 "-")))
          #_(doseq [{:keys [name version id assets]} @components]
            (let [asset (first assets)
                  uploader (:uploader asset)]
              (println
               (str (when repo-name
                      (str repo-name "/")) name ":" version "\t uploaded by: " uploader))))
          (println new-token)
          (recur))))))

(defn -main [& args]
  (let [{:keys [options arguments errors summary]} (parse-opts args  global-cli-options)
        config (:config options)
        action (first arguments)
        op-name (keyword (:doc options))
        repo-name (:repository options)
        cfg (load-config! config)]
    #_(println "HHHere is config:" cfg "\n first arg: " "\n options: " options "\n arguments: " arguments "\n summary: " summary)
    (cond
      errors (error-msg errors)
      (nil? (:config options)) (error-msg "No config provided \n --config [file-name]>")
      (:help options) (usage summary)
      (:doc options) (println (doc (load-api) op-name))
      :else (case action
              "list" (cond
                       (not (:repository options)) (list-repos cfg)
                       (and (:repository options) (:image options)) (println "called --repo and --image")
                       (:repository options) (list-images repo-name cfg)
                       )

              "delete" (println (+ 2 2))))))



