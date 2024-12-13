(ns net.gosha.atproto.fetch
  (:require
   [martian.core :as martian]
   [clojure.java.io :as io]))

(def api-url "https://public.api.bsky.app/xrpc/")

(def openapi-spec
  (-> (io/resource "openapi/api.json")
      slurp))

(def client
  (martian/bootstrap-openapi api-url "resources/openapi/api.json"))

;; (def client
;;   (martian/bootstrap-openapi api-url openapi-spec))

(comment
  (println openapi-spec)
  
  (martian/explore client) ;; []
  
  (martian/response-for client 
                       :app.bsky.feed.getAuthorFeed 
                       {:actor "chaselambert.dev"})
  ,)
