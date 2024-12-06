(ns net.gosha.atproto.fetch
  (:require
   [aero.core                :as aero]
   [clojure.java.io          :as io]
   [java-time.api            :as jt]
   [net.gosha.atproto.client :as client]
   [net.gosha.atproto.core   :as core]))

(defn fetch-author-feed
  "GET request for user's posts.
   Fetches and filters posts from the last N days."
  [actor & {:keys [limit cursor days-ago]
            :or   {limit    50
                   days-ago 7}}]
  (let [cutoff    (jt/local-date-time (jt/minus (jt/local-date) (jt/days days-ago)))
        params    {:actor  actor
                   :limit  limit
                   :cursor cursor}
        response  (:body (client/get-req
                           "/xrpc/app.bsky.feed.getAuthorFeed"
                           {:params params}))
        posts     (get-in response [:feed])
        in-range? (fn [post]
                   (let [post-date (-> post 
                                     (get-in [:post :indexedAt])
                                     jt/instant
                                     (jt/local-date-time "UTC"))]
                     (jt/not-before? post-date cutoff)))]
    {:feed   (filterv in-range? posts)
     :cursor (:cursor response)}))

(comment
  ;; Still looking for a way to read data without this auth setup
  (let [dev-config (aero/read-config (io/resource "dev-config.edn"))]
    (core/init 
     {:base-url     "https://bsky.social"
      :username     (:username dev-config)
      :app-password (:password dev-config)}))
  
  (client/authenticate!)
  
  (fetch-author-feed "chaselambert.dev" :limit 5)
  (fetch-author-feed "vanderhart.net"   :days-ago 2 :limit 3)
  (fetch-author-feed "vanderhart.net"   :limit 5)

  ;; First request gets initial batch and a cursor
  (let [first-response (fetch-author-feed "vanderhart.net" :limit 10)
        cursor        (:cursor first-response)]
    
    ;; Use that cursor to get next batch
    (when cursor
      (fetch-author-feed "vanderhart.net" 
                        :limit 10 
                        :cursor cursor)))
  
  (let [sample-date "2024-12-04T23:35:03.023Z"
        cutoff      (jt/local-date-time (jt/minus (jt/local-date) (jt/days 1)))]
    {:parsed-date (-> sample-date jt/instant (jt/local-date-time "UTC"))
     :cutoff      cutoff
     :in-range?   (jt/not-before? 
                    (-> sample-date jt/instant (jt/local-date-time "UTC")) 
                    cutoff)})
  
  
  ,)
