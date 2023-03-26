(ns nexus-api-client.jvm-runtime
  (:require
   [pem-reader.core :as pem]
   [unixsocket-http.core :as http]
   [clj-http.client :as c])
  (:import
   [java.security KeyPair]
   [java.security.cert X509Certificate]
   [okhttp3 OkHttpClient$Builder]
   [okhttp3.tls HandshakeCertificates$Builder HeldCertificate]))

(defn- read-cert
  "Loads a PEM file from a given path and returns the certificate from it."
  [path]
  (-> path
      (pem/read)
      (:certificate)))

(defn- make-builder-fn
  "Creates a builder fn to load the certs for mTLS.
  This is expected by unixsocket-http underlying mechanism."
  [{:keys [ca cert key]}]
  (let [{:keys [public-key private-key]} (pem/read key)
        key-pair (KeyPair. public-key private-key)
        held-cert (HeldCertificate. key-pair (read-cert cert))
        handshake-certs (-> (HandshakeCertificates$Builder.)
                            (.addTrustedCertificate (read-cert ca))
                            (.heldCertificate held-cert (into-array X509Certificate []))
                            (.build))]
    (fn [^OkHttpClient$Builder builder]
      (.sslSocketFactory builder
                         (.sslSocketFactory handshake-certs)
                         (.trustManager handshake-certs)))))

(defn client
  [uri opts]
  (http/client uri
               (assoc opts
                      :mode :recreate
                      :builder-fn (if-let [mtls (:mtls opts)]
                                    (make-builder-fn mtls)
                                    identity))))

(defn request
  "Internal fn to perform the request."
  [{:keys [client method path headers query-params body as throw-exceptions throw-entire-message]}]
  (-> {:client client
       :method method
       :url path
       :headers headers
       :query-params query-params
       :body body
       :as (if (or (= :data as)
                   (nil? as))
             :string
             as)
       :throw-exceptions throw-exceptions
       :throw-entire-message? throw-entire-message}
      (http/request)
      (:body)))

(comment
  (def client (http/client "unix:///var/run/docker.sock"))
  (def client (http/client "tcp://127.0.0.1:8081"))
  (println client)
  (http/get client "/_ping")
  (+ 2 2)

  (c/get "http://google.com")

  (c/get "http://localhost:8081/service/rest/v1/components?repository=docker")

(c/get "http://localhost:8081/service/rest/v1/components/ZG9ja2VyOmY1NWUyY2FkNDBlZjI0OWFiNjc0MjIyMDc1N2NhNjk1")
  

(c/delete "http://localhost:8081/service/rest/v1/components/ZG9ja2VyOjdmMDZjOGViMzQ2N2JkOWEyNWY0OTUwOWY4ODYxNWFh" {:basic-auth ["admin" "admin"]})

  0
  )