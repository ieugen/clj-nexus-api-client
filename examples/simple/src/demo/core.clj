(ns demo.core
  (:require [nexus-api-client.core :as c]
            [nexus-api-client.interface :as interface]))

(comment 
  
  (c/doc (interface/load-api) :getRepositories)
  
  (System/getenv "NEXUS_ADMIN_PASS")
  
  
  )