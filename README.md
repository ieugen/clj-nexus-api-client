

This project was heavily inspired from  [contajners](https://github.com/lispyclouds/contajners) and uses Open API Document from <nexus_url>/service/rest/swagger.json.
Based on this document, some operations can be performed on the Sonatype Nexus repository manager.

A comprehensive listing of REST API endpoints and functionality is documented [here](https://help.sonatype.com/repomanager3/integrations/rest-and-integration-api).

**The README here is for the current local-nexus branch and _may not reflect the released version_.**

## Installation

#### Clojure CLI/deps.edn

Using with deps.edn

```clojure
com.github.ieugen/clj-nexus-api-client {:git/sha "<COMMIT ID HERE>"}
```
## Usage

### An example REPL session

The service is assumed to be running on `http://localhost:8081` .
The project contains instructions on how to setup a local Sonatype Nexus using docker-compose.

## Setup Sonatype Nexus using docker compose

```sh
# start sonatype nexus container in detached mode
docker compose up -d

# list logs and follow logs
docker compose logs -f

# Capture admin password that is generated
export NEXUS_ADMIN_PASS=$(docker exec -ti clj-nexus-api-client-nexus-1 cat /nexus-data/admin.password)
echo $NEXUS_ADMIN_PASS

# Now you can start clojure in the same terminal and access the the env var
# Update the :git/sha value to the latest main commit id
clojure -Sdeps '{:deps {com.github.ieugen/clj-nexus-api-client {:git/sha "7b686a3bffcbf29a3de86711e588a80afea8c237"}}}'

Cloning: https://github.com/ieugen/clj-nexus-api-client.git
Checking out: https://github.com/ieugen/clj-nexus-api-client.git at ff6257357ef9464a42f9b8a528d240c44d44446a
Clojure 1.11.1
user=>

```

Once you have clojure up

```clojure
user=> (require '[nexus-api-client.core :as c] '[nexus-api-client.interface :as interface])
nil

;; get nexus passowrd from environment
user=> (def nexus-pass (System/getenv "NEXUS_ADMIN_PASS"))
#'user/nexus-pass

user=> (c/ops (interface/load-api))
(:getPrivileges
 :getSystemStatusChecks
 :getRepository_24
 :updateRepository_26
 :updateRepository_30
 :getRepository_2
 :getRepository_21
 :deleteRoutingRule
 :getRepository_36
 :createRepository_14
 :createRepository_5
 :getRepository_10
 :invalidateCache
 :updateRepository_7
 :createRepository_21
 :createPrivilege_2
 :updateRepository_34
 :uploadComponent
 :getTrustStoreCertificates
 :get
 :getActiveRealms
 :getRoles
 :createRepository_4
 :createRepository_10
 :read_1
 :getPrivilege
 :run
 :updateRepository_40
 :createPrivilege_1
 :getRepository_34
 :createBlobStore
 :updateRepository_19
 :deleteLdapServer
 :createRepository_23
 :getRepositories_1
 :getRepository_6
 :deleteUser
 :updateRepository_3
 :changeOrder
 :getRepository_23
 :read
 :createPrivilege
 :freeze
 :getRepository_13
 :getRepository_22
 :getConfiguration
 :removeCertificate
 :getRepository_38
 :createRepository_17
 :updateRepository_12
 ...)


user=> (c/doc (interface/load-api) :getRepository)
{:method :get,
 :path "/v1/repositories/{repositoryName}",
 :params ({:in :path, :name "repositoryName"}),
 :summary "Get repository details",
 :doc-url "http://localhost:8081/service/rest/v1/repositories/{repositoryName}"}

 user=> (c/doc (interface/load-api) :getAssetById)
{:method :get,
 :path "/v1/assets/{id}",
 :params ({:in :path, :name "id"}),
 :summary "Get a single asset",
 :doc-url "http://localhost:8081/service/rest/v1/assets/{id}"}


user=> (c/invoke {:endpoint "http://localhost:8081/service/rest"
           :creds {:user "admin" :pass nexus-pass}}
          {:operation :getRepository
           :params {:repositoryName "docker"}})
"curl -u admin:admin -X GET http://localhost:8081/service/rest/v1/repositories/docker"


user=> (c/invoke {:endpoint "http://localhost:8081/service/rest"
           :creds {:user "admin" :pass nexus-pass}}
          {:operation :getAssetById
           :params {:id "bWF2ZW4tY2VudHJhbDozZjVjYWUwMTc2MDIzM2I2MjRiOTEwMmMwMmNiYmU4YQ'"}})
"curl -u admin:admin -X GET http://localhost:8081/service/rest/v1/assets/bWF2ZW4tY2VudHJhbDozZjVjYWUwMTc2MDIzM2I2MjRiOTEwMmMwMmNiYmU4YQ"

```


## CLI app with [Babashka](https://babashka.org/)

- you must have babashka installed locally
- createa bb.edn to run nexus client as a babashka task
```clojure
{:min-bb-version "1.3.184"
 :deps {io.github.ieugen/clj-nexus-api-client {:git/sha "0a760be5a182aafba07cd980d3a5f5959fee7025"}}
 :tasks {:requires ([nexus-api-client.cli :as n])
         nexus {:doc "Curățenie în imagini docker"
                :task (apply n/-main *command-line-args*)}}}
```
- this app is designed to access sonatype nexus api endpoint and list and delete docker images;
- by default, it loads a file named "config.edn" from the root of the project,
  which contains the config options:
        
        {:endpoint "https://sonatype-nexus-url/service/rest" 
         :user "username" 
         :pass "password"}

- you can change the config by creating "another-file.edn" with the above structure
 and pass the config:

 ```sh
 bb nexus --config another-file.edn list
 ```

### Usage:
 list [params]
 delete [params] - all params are required for this action
### Parameters:"
  -r or --repository <repository-name>
  -i or --image <image-name>
  -t or --tag <image-tag>
  --dry-run - used only with 'delete' action to list the images that will be deleted

  NOTE: <image-name> and <image-tag> can be pattern matching names (wildcards):
  
  
  ```sh
  bb nexus list -r repo-1 -i dev* -t beta* 
  ```

### Examples
- list :
      bb list  - list all the repositories
      bb list -r <repo-name>  -> list all the images
      bb list -r <repo-name> -i <image-name> -> list all the tags for a specific image
 - delete : 
      bb delete -r <repo-name> -i <image-name> -t <version> --dry-run -> dry-runs the delete process with information about the images that will be deleted
      bb delete -r <repo-name> -i <image-name> -t <version> -> deletes the images with the specified tag

    'bb nexus list' - list all the repositories
    'bb nexus list -r repo-1' - lists all the images from repo-1 repository
    'bb nexus list -r repo-1 -i app-dev' - lists all tags for images in repo-1 repository
    'bb nexus list -r repo-1 -i app-dev -t prod* ' - lists all the images named app-dev, tagged with prod* from repo-1
    'bb nexus delete -r repo-1 -i app-dev -t prod* --dry-run - lists all the images named app-dev, tagged with prod* from repo-1 which will be deleted
    'bb nexus delete -r repo-1 -i app-dev -t prod* - deletes the images named app-dev, tagged with prod* from repo-1"





