(ns net.gosha.atproto.fetch
  (:require
   [org.httpkit.client       :as http]
   [clojure.data.json        :as json]
   [clojure.java.io          :as io]
   [aero.core                :as aero]
   [java-time.api            :as jt]
   [net.gosha.atproto.core   :as core]
   [net.gosha.atproto.client :as client]))

(defn fetch-author-feed
  "GET request for a user's posts with authentication"
  [actor & {:keys [limit cursor]
            :or   {limit 50}}]
  (let [params {:actor  actor
                :limit  limit
                :cursor cursor}]
    (client/get-req 
     "/xrpc/app.bsky.feed.getAuthorFeed"
     {:params params})))

(comment
  ;; First initialize and authenticate using existing setup
  (let [dev-config (aero/read-config (io/resource "dev-config.edn"))]
    (core/init 
     {:base-url     "https://bsky.social"
      :username     (:username dev-config)
      :app-password (:password dev-config)}))
  
  (client/authenticate!)
  
  ;; Now try the fetch
  (fetch-author-feed "chaselambert.dev" :limit 10))
