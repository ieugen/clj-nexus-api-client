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
             (str/join  \newline errors))))

(def global-cli-options
  [[nil "--config NAME" "Configuration file name" :default "config.edn"]
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

(defn get-images-data [cfg repo-name]
  (let [{:keys [endpoint user pass]} cfg
        conn {:endpoint endpoint
              :creds {:user user :pass pass}}
        opts {:operation :getComponents
              :params {:repository repo-name}}
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

(defn print-image-format [image]
  (let [{:keys [id repository name tag]} image
        repo (str repository "/" name ":" tag)]
    (println repo "\t\t" id)))

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
              :params {:name id}}]
    #_(cc/invoke conn opts) ;; uncomment this line to delete images
    (println ":deleteComponent " id)))

(defn dry-run-delete [cfg repo image tag]
  (let [images (images-new-structure cfg repo image tag)]
    (doall (map #(do (println "Deleting image: ") 
                     (print-image-format %)) images))))

(defn delete-images-by-tag [cfg repo image tag]
  (let [images (images-new-structure cfg repo image tag)]
    (doall (map #(delete-image cfg %) images))))


(defn -main [& args]
  (let [{:keys [options arguments errors summary]} (parse-opts args  global-cli-options)
        config (:config options)
        action (first arguments)
        repo-name (:repository options)
        image-name (:image options)
        version (:tag options)
        cfg (cc/load-config! config)]
    (cond
      errors (error-msg errors)
      (nil? (:config options)) (error-msg "No config provided \n --config [file-name]>")
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
