(ns net.gosha.atproto.feed
  (:require [clojure.tools.logging    :as log]
            [net.gosha.atproto.client :as at]
            [clojure.core.async       :as a])
  (:import  [java.time Instant]
            [java.time.temporal ChronoUnit]
            [java.util Date]))

(defn get-author
  "Fetch and filter posts from an author since N days ago. Uses cursor-based
   pagination to walk through the author's feed until reaching posts older than
   the cutoff date, avoiding fetching the entire history.

   Places each post on the supplied result channel.

   Parameters:
   - session:   API session from (init) for making authenticated requests
   - actor:     The handle of the user (e.g. 'user.bsky.social')
   - result-ch: core.async channel where posts will be placed

   - opts: Optional map of parameters:
     - :start-ts   - Retrieve posts after this timestamp (default: 1 month)
     - :batch-size - Posts per request (default 100, max 100)
     - :max-posts  - Maximum number of posts to return
     - :filter     - one of 'posts_with_replies', 'posts_no_replies',
       'posts_with_media', 'posts_and_author_threads'

   Returns a channel of posts, each containing the full post data structure
   from the Bluesky API."
  [session actor result-ch & {:keys [batch-size start-ts max-posts]
                              :or   {batch-size 100
                                     max-posts  Long/MAX_VALUE
                                     start-ts   (.minus
                                                  (Instant/now)
                                                  30 ChronoUnit/DAYS)}}]
  (a/go-loop [cursor nil ; Pagination cursor from API
              post-count 0] ; Request counter for safety limit
    (if (<= max-posts post-count)
      (a/close! result-ch)
      (let [params {:limit batch-size :actor actor}
            params (if cursor (assoc params :cursor cursor) params)
            _ (log/info "Making request")
            response (a/<! (at/call-async session
                             :app.bsky.feed.get-author-feed params))
            body (:body response)
            all-posts (:feed body)
            start-ts (if (instance? Date start-ts)
                       (.toInstant start-ts)
                       start-ts)
            posts (take-while (fn [post]
                                (let [post-ts (get-in post
                                                [:post :record :createdAt])
                                      post-inst (Instant/parse post-ts)]
                                  (.isAfter
                                    ^Instant post-inst
                                    ^Instant start-ts)))
                    all-posts)]
        (if (empty? posts)
          (a/close! result-ch)
          (do
            (doseq [post posts]
              (a/>! result-ch post))
            (if (or (not (:cursor body)) (not= all-posts posts))
              (a/close! result-ch)
              (recur (:cursor body) (+ post-count (count posts)))))))))
  result-ch)

(defn get-authors
  "Fetch recent posts from multiple authors concurrently.

   Parameters:
   - session: API session from (init)
   - authors: Vector of author handles
   - opts:    Optional parameters passed to posts-since

   Returns: Channel containing all posts from all authors."
  [session authors & opts]
  (let [result-ch  (a/chan 10000)]
    (a/go
      (doseq [author authors]
        (let [author-ch (get-author session author (a/chan 1000) opts)
              posts     (a/<! (a/into [] author-ch))]
          (doseq [post posts]
            (a/>! result-ch post))))
      (a/close! result-ch))
    result-ch))

(comment

  (def client (at/init :base-url "https://public.api.bsky.app"))

  (def ch-1 (get-author client "atproto.com" (a/chan 10000)))
  (def ch-2 (get-author client "bsky.app" (a/chan 10000)))

  (def all-1 (a/<!! (a/into [] ch-1)))
  (def all-2 (a/<!! (a/into [] ch-2)))

  (def authors ["atproto.com" "bsky.app"])
  (def mixed-ch (get-authors client authors))
  (def all (a/<!! (a/into [] mixed-ch)))

  (count all-1) ;;  7
  (count all-2) ;; 12

  (count all)   ;; 19

  ,)
