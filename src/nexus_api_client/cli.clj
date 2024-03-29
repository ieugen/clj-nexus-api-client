(ns nexus-api-client.cli
  (:require [clojure.tools.cli :refer [parse-opts]]
            [clojure.string :as str]
            [nexus-api-client.cli-core :as cc]))

(defn usage [options-summary]
  (println (->> ["Usage:"
                  " list [params]"
                  " delete [params] - all params are required for this action"
             ""
             "Parameters:"
             "  -r or --repository <repository-name>"
             "  -i or --image <image-name>"
             "  -t or --tag <image-tag"
             "  --dry-run - used only with 'delete' action to list the images that will be deleted"
             "Options:"
             options-summary]
            (str/join \newline))))

(defn error-msg [errors]
  (binding [*out* *err*]
    (println "The following errors occurred while parsing your command:\n\n"
             errors)))

(defn valid-config?
    "Validate a nexus configuration for required options.
   If valid, return true.
   If invalid return map with reasons why validation failed."
    [config]
    (if (map? config)
      (let [endpoint (:endpoint config)
            user (:user config)
            pass (:pass config)]
        (if (and endpoint user pass) 
          true
          {:errors [(str "Missing keys in configuration: " (when (nil? endpoint) ":endpoint ") (when (nil? user) ":user ") (when (nil? pass) ":pass") )]}))
      {:errors ["Config is nil or not a map"]}))

(def global-cli-options
  [[nil "--config-file NAME" "Configuration file name"]
   [nil "--config NAME" "Configuration as edn"]
   ["-r" "--repository NAME" "list the images for a specific repository"]
   ["-i" "--image NAME" "list the tags for a specific image"]
   ["-t" "--tag NAME" "filter images by tag name"]
   [nil "--dry-run" "Lists the images that will be deleted"]
   ["-h" "--help"]])

(defn get-repos-data [cfg]
  (let [{:keys [endpoint user pass]} cfg
        conn {:endpoint endpoint
              :creds {:user user :pass pass}}
        opts {:operation :getRepositories_1}
        repos (cc/invoke conn opts)]
    repos))

(defn list-repos [cfg]
  (let [repos (get-repos-data cfg)]
    (println "Nexus docker repositories: ")
    (println (apply str (repeat 25 "-")))
    (doseq [{:keys [name]} repos] (println name))))


(defn get-images-data-by-version [cfg repo-name image-name version]
  (let [{:keys [endpoint user pass]} cfg
        conn {:endpoint endpoint
              :creds {:user user :pass pass}}
        opts {:operation :search
              :params {:repository repo-name}}
        opts (if image-name (assoc-in opts [:params :name] image-name) opts)
        opts (if version (assoc-in opts [:params :version] version) opts)
        components (atom [])]
    (loop [token nil]
      (let [opts (if token (assoc-in opts [:params :continuationToken] token) opts)
            response (cc/invoke conn opts)
            items (:items response)
            new-token (:continuationToken response)]
        (swap! components concat items)
        (when new-token
          (recur new-token))))
    @components))

(defn select-image-fields [image]
  (let [id (:id image)
        repo (:repository image)
        image-name (:name image)
        tag (:version image)]
    {:id id :repository repo :name image-name :tag tag}))


(defn images-new-structure
  ([cfg repo-name]
   (images-new-structure cfg repo-name nil))
  ([cfg repo-name image-name]
   (images-new-structure cfg repo-name image-name nil))
  ([cfg repo-name image-name version]
   (let [data (get-images-data-by-version cfg repo-name image-name version)]
     (map select-image-fields data))))

(defn image->str-plain
  "Image plain string represenataion."
  [image]
  (let [{:keys [id repository name tag]} image]
    (str repository "/" name ":" tag "\t\t" id)))

(defn print-image-format [image]
  (println (image->str-plain image)))

(defn list-images
  ([cfg repo-name]
   (list-images cfg repo-name nil))

  ([cfg repo-name image-name]
   (list-images cfg repo-name image-name nil))

  ([cfg repo-name image-name version]
   (let [images (images-new-structure cfg repo-name image-name version)]
     (doall (map print-image-format images)))))

(defn delete-image [cfg image]
  (let [{:keys [id]} image
        {:keys [endpoint user pass]} cfg
        conn {:endpoint endpoint
              :creds {:user user :pass pass}}
        opts {:operation :deleteComponent
              :params {:id id}}]
    (println "Deleting image: " (image->str-plain image))
    (cc/invoke conn opts)))

(defn dry-run-delete [cfg repo image tag]
  (let [images (images-new-structure cfg repo image tag)]
    (doall (map #(do (println "Deleting image: ")
                     (print-image-format %)) images))))

(defn delete-images-by-tag [cfg repo image tag]
  (let [images (images-new-structure cfg repo image tag)]
    (doall (map #(delete-image cfg %) images))))


(defn -main [& args]
  (let [parsed-options (parse-opts args  global-cli-options)
        {:keys [options arguments errors summary]} parsed-options 
        config-file (:config-file options)
        config (:config options)
        action (first arguments)
        repo-name (:repository options)
        image-name (:image options)
        version (:tag options)
        cfg (cc/load-config! config-file config)
        valid-cfg? (valid-config? cfg)]
    (cond
      errors (error-msg errors)
      (:errors valid-cfg?) (error-msg (:errors valid-cfg?))
      (:help options) (usage summary)
      :else (case action
              "list" (cond
                       (not (:repository options)) (list-repos cfg)
                       (and repo-name image-name version) (list-images cfg repo-name image-name version)
                       (and (:repository options) (:image options)) (list-images cfg repo-name image-name)
                       (:repository options) (list-images cfg repo-name))

              "delete" (cond
                         (or (not (:repository options))
                             (not (:image options))
                             (not (:tag options)))
                         (println "To delete an image, you must provide repository-name, image-name and version")
                         (:dry-run options) (dry-run-delete cfg repo-name image-name version)
                         (and (:repository options)
                              (:image options)
                              (:tag options)) (delete-images-by-tag cfg repo-name image-name version))
              (usage summary)))))
