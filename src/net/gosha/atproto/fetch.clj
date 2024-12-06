(ns net.gosha.atproto.fetch
  (:require
   [net.gosha.atproto.client :as client]
   [clojure.java.io          :as io]
   [aero.core                :as aero]
   [java-time.api            :as jt]))

(defn get-user-posts
  "Fetch posts from a user within the last N days.
   Returns a lazy sequence of posts to handle large result sets.
   
   user     - The handle or DID of the user
   days-ago - Number of days back to fetch (default 7)
   limit    - Max posts per request (default 100)"
  [user & {:keys [days-ago limit]
           :or   {days-ago 7
                  limit    100}}]
  (let [cutoff (jt/minus (jt/local-date) (jt/days days-ago))]
    (loop [posts   []
           cursor  nil]
      (let [response (client/get-req 
                      "/xrpc/app.bsky.feed.getAuthorFeed"
                      {:params {:actor  user
                                :cursor cursor 
                                :limit  limit}})
            new-posts (get-in response [:feed :posts])
            earliest  (some-> new-posts 
                            last 
                            :indexed-at
                            (jt/local-date "yyyy-MM-dd'T'HH:mm:ss.SSSX"))]
        (cond
          (empty? new-posts)           posts  ; No more posts
          (jt/before? earliest cutoff) posts  ; Past our date range
          :else (recur (into posts new-posts)
                      (get-in response [:cursor])))))))

(defn get-list-posts
  "Fetch posts from all users in a list within the last N days.
   Returns a lazy sequence of posts to handle large result sets.
   
   list-uri - The URI of the list
   days-ago - Number of days back to fetch (default 7)
   limit    - Max posts per request (default 100)"
  [list-uri & {:keys [days-ago limit]
               :or   {days-ago 7
                      limit    100}}]
  (let [cutoff (jt/minus (jt/local-date) (jt/days days-ago))]
    (loop [posts   []
           cursor  nil]
      (let [response (client/get-req 
                      "/xrpc/app.bsky.feed.getListFeed"
                      {:params {:list   list-uri
                                :cursor cursor
                                :limit  limit}})
            new-posts (get-in response [:feed :posts])
            earliest  (some-> new-posts 
                            last 
                            :indexed-at
                            (jt/local-date "yyyy-MM-dd'T'HH:mm:ss.SSSX"))]
        (cond
          (empty? new-posts)           posts  ; No more posts
          (jt/before? earliest cutoff) posts  ; Past our date range
          :else (recur (into posts new-posts)
                      (get-in response [:cursor])))))))

(comment
  ;; 1. Check if config loaded
  (println "Current config:" @net.gosha.atproto.core/config)
  
  ;; 2. Initialize with config
  (let [dev-config (aero/read-config (io/resource "dev-config.edn"))]
    (println "Loaded dev config:" dev-config)
    (net.gosha.atproto.core/init 
     {:base-url     "https://bsky.social"
      :username     (:username dev-config)
      :app-password (:password dev-config)}))
  
  ;; 3. Check config again
  (println "Updated config:" @net.gosha.atproto.core/config)
  
  ;; 4. Authenticate
  (net.gosha.atproto.client/authenticate!)
  
  ;; 5. Check config has auth token
  (println "Auth token present?" (boolean (:auth-token @net.gosha.atproto.core/config)))
  
  ;; 6. Now try getting posts
  (get-user-posts "chaselambert.dev" :days-ago 2 :limit 50))
