(ns net.gosha.atproto.fetch
  (:require
   [aero.core                :as aero]
   [clojure.java.io          :as io]
   [java-time.api            :as jt]
   [net.gosha.atproto.client :as client]
   [net.gosha.atproto.core   :as core]))

(defn fetch-author-feed
  "GET request for user's posts using existing client infrastructure"
  [actor & {:keys [limit cursor]
            :or   {limit 50}}]
  (let [params {:actor  actor
                :limit  limit
                :cursor cursor}]
    (:body (client/get-req 
            "/xrpc/app.bsky.feed.getAuthorFeed"
            {:params params}))))

(comment
  ;; Still looking for a way to read data without this auth setup
  (let [dev-config (aero/read-config (io/resource "dev-config.edn"))]
    (core/init 
     {:base-url     "https://bsky.social"
      :username     (:username dev-config)
      :app-password (:password dev-config)}))
  
  (client/authenticate!)
  
  (fetch-author-feed "chaselambert.dev" :limit 10)
  (fetch-author-feed "vanderhart.net"   :limit 10))
