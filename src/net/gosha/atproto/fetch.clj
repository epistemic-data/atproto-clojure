(ns net.gosha.atproto.fetch
  (:require [clojure.tools.logging :as log]
            [net.gosha.atproto.client :as at]
            [clojure.core.async :as a])
  (:import [java.time Instant]
           (java.time.temporal ChronoUnit TemporalUnit)
           (java.util Date)))

(defn posts-since
  "Fetch and filter posts from an author since N days ago. Uses cursor-based
   pagination to walk through the author's feed until reaching posts older than
   the cutoff date, avoiding fetching the entire history.

   Places each post on the supplied result channel.

   Parameters:
   - actor: The handle of the user (e.g. 'user.bsky.social')

   - opts: Optional map of parameters:
     - :start-ts: Retrieve posts after this timestamp (default: 1 month)
     - :batch-size - Posts per request (default 100, max 100)
     - :max-posts - Maximum number of posts to return
     - :filter - one of 'posts_with_replies', 'posts_no_replies',
       'posts_with_media', 'posts_and_author_threads'

   Returns a channel of posts, each containing the full post data structure
   from the Bluesky API."
  [session actor result-ch & {:keys [batch-size start-ts max-posts]
                              :or   {batch-size 100
                                     max-posts Long/MAX_VALUE
                                     start-ts (.minus
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

(comment

  (def client (at/init :base-url "https://public.api.bsky.app"))

  (def ch (posts-since client "marisakabas.bsky.social" (a/chan 10000)))

  (class (a/<!! rch))

  (def all (a/<!! (a/into [] rch)))

  )



