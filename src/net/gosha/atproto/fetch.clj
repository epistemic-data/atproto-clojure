(ns net.gosha.atproto.fetch
  (:require
   [martian.core    :as martian]
   [martian.httpkit :as martian-http])
  (:import
   [java.time Instant]
   [java.time.temporal ChronoUnit]))

(def openapi-url 
  "https://raw.githubusercontent.com/bluesky-social/bsky-docs/refs/heads/main/atproto-openapi-types/spec/api.json")

(def client 
  (martian-http/bootstrap-openapi
    openapi-url
    {:server-url "https://public.api.bsky.app"}))

(defn author-feed
  "Fetch an author's feed from Bluesky.
   Required params:
   - actor: The handle of the user (e.g. 'user.bsky.social')
   Optional params:
   - limit: Number of posts (1-100, default 50)
   - cursor: Pagination cursor
   - filter: Type of posts to include
   - include-pins: Whether to include pinned posts"
  [actor & [opts]]
  (:body @(martian/response-for client 
                              :app.bsky.feed.get-author-feed 
                              (merge {:actor actor} opts))))


(defn parse-iso-instant
  "Parse an ISO-8601/RFC3339 datetime string to java.time.Instant"
  [datetime-str]
  (Instant/parse datetime-str))

(defn posts-since
  "Fetch and filter posts from an author since N days ago. Uses cursor-based 
   pagination to walk through the author's feed until reaching posts older than
   the cutoff date, avoiding fetching the entire history.

   Parameters:
   - actor: The handle of the user (e.g. 'user.bsky.social')
   - days-ago: Number of days to look back
   - opts: Optional map of parameters:
     - :batch-size   - Posts per request (default 100, max 100)
     - :max-requests - Safety limit on API calls (default 10)

   Returns a vector of posts, each containing the full post data structure
   from the Bluesky API."
  [actor days-ago & [{:keys [batch-size max-requests]
                      :or   {batch-size  100
                             max-requests 10}}]]
  (let [cutoff-date (.minus (Instant/now) days-ago ChronoUnit/DAYS)]
    (loop [results  []           ; Accumulator for filtered posts
           cursor   nil          ; Pagination cursor from API
           requests 0]           ; Request counter for safety limit
      (let [;; Only include cursor in params when we have one
            params     (cond-> {:limit batch-size}
                        cursor (assoc :cursor cursor))
            response   (author-feed actor params)
            posts      (get-in response [:feed])
            new-cursor (get response :cursor)]
        (cond
          ;; Safety limit reached - return what we have
          (>= requests max-requests)
          results
          
          ;; No more posts available
          (empty? posts)
          results
          
          :else
          (let [;; Get date of oldest post in current batch
                oldest-post-date (some-> posts
                                        last
                                        (get-in [:post :record :createdAt])
                                        parse-iso-instant)
                ;; Filter posts in current batch by date
                filtered-posts   (filter #(.isAfter
                                            (parse-iso-instant
                                              (get-in % [:post :record :createdAt]))
                                            cutoff-date)
                                       posts)]
            
            ;; If oldest post is still newer than cutoff and we have more pages,
            ;; continue fetching. Otherwise return accumulated results
            (if (and oldest-post-date
                     (.isAfter oldest-post-date cutoff-date)
                     new-cursor)
              (recur (into results filtered-posts)
                     new-cursor
                     (inc requests))
              
              (into results filtered-posts))))))))

(comment
  ;; Basic feed fetch
  (author-feed "atproto.com")
  (martian/explore client)
  
  ;; With options
  (author-feed "atproto.com" 
               {:limit        10
                :filter       "posts_no_replies"
                :includePins  true})
  
  ;; Get profile info (with proper dereferencing)
  (-> (martian/response-for client 
                          :app.bsky.actor.get-profile 
                          {:actor "atproto.com"})
      deref
      :body)

    ;; Get all posts from last 7 days
  (posts-since "atproto.com" 7)
  
  ;; Get posts from last 30 days, willing to make more requests
  (posts-since "atproto.com" 30 
              {:max-requests 20})
  
  ;; Process only text content from last week
  (->> (posts-since "atproto.com" 7)
       (map #(get-in % [:post :record :text]))
       (remove nil?))
  ,)
