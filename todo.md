# Todo list

- pt imagini docker(api de component) - sa se poata lista toate versiunile (taguri) unei imagini
- sa poata fi sterse unele imagini

- nume-imagine + versiune

- docker compose ptr nexus

## Components API
* List components from the docker repo
    - docker exec -ti clj-nexus-api-client-nexus-1 curl -u admin:admin -X GET 'http://localhost:8081/service/rest/v1/assets?repository=docker' -> resources/sonatype-nexus/docker-components.json
 


## Referinte

- https://hub.docker.com/r/sonatype/nexus3/
- https://help.sonatype.com/repomanager3/integrations/rest-and-integration-api
- https://docs.docker.com/registry/insecure/#deploy-a-plain-http-registry
