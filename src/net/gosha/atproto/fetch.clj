(ns net.gosha.atproto.fetch
  (:require
   [martian.core      :as martian]
   [martian.httpkit   :as martian-http]
   [clojure.java.io   :as io]
   [charred.api       :as json])
  (:import
   [java.time Instant LocalDateTime ZoneOffset]))


(def api-url "https://public.api.bsky.app")
(def openapi-url 
  "https://raw.githubusercontent.com/rdmurphy/atproto-openapi-types/main/spec/api.json")

(defn load-openapi-spec []
  (try 
    (-> (slurp openapi-url)
        (json/read-json :key-fn keyword))
    (catch Exception _
      (-> (io/resource "openapi/api.json")
          slurp
          (json/read-json :key-fn keyword)))))

(defn create-client []
  (let [spec (load-openapi-spec)]
    (martian-http/bootstrap-openapi 
     api-url 
     {:server-url api-url}
     spec)))

(comment
  (def client (create-client))
  
  ;; Let's look at what martian sees
  (def spec (load-openapi-spec))
  
  ;; Look at operationIds for feed endpoints
  (->> spec 
       :paths 
       vals 
       (map #(get-in % [:get :operationId]))
       (filter #(when % (clojure.string/includes? % "feed")))
       sort))
