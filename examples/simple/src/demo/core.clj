(ns demo.core
  (:require [nexus-api-client.core :as c]
            [nexus-api-client.interface :as interface]))

(comment

  (interface/doc (c/load-api) :getRepositories)

  (System/getenv "NEXUS_ADMIN_PASS")


  (let [uri (java.net.URI/create "file:///var/run/docker.sock")]
    (println uri "host: " (.getHost uri) ))

  )