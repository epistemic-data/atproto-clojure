(ns net.gosha.atproto.fetch
  (:require
   [martian.core    :as martian]
   [martian.httpkit :as martian-http]))

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
  ,)
