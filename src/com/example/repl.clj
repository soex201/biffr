(ns com.example.repl
  (:require 
    [com.biffweb :as biff :refer [q]]
    [xtdb.api :as xt]))

(defn get-sys []
  (biff/assoc-db @biff/system))

(comment

  ;; If I eval (biff/refresh) with Conjure, it starts sending stdout to Vim.
  ;; fix-print makes sure stdout keeps going to the terminal.
  (biff/fix-print (biff/refresh))

  (:xt/id (first (let [{:keys [biff/db] :as sys} (get-sys)]
    (q db
       '{:find (pull user [*])
         :where [[user :user/email]]}))))
 
  (biff/lookup-id (:biff/db (get-sys))
    :user/email "abc@example.com")
  
  (biff/submit-tx
    (get-sys)
    [{:db/doc-type :post
      :post/user (biff/lookup-id (:biff/db (get-sys)) :user/email "abc@example.com")
      :post/title "A post test number three"
      :post/body "a bit of a post test number three, hopefully it works"
      :post/created (java.util.Date.)}])
  
  (let [{:keys [biff/db] :as sys} (get-sys)]
    (q db
      '{:find (pull post [*])
        :where [[post :post/title]]}))
  
  (sort-by :post/created #(compare %2 %1) (let [{:keys [biff/db] :as sys} (get-sys)]
    (q db
      '{:find (pull post [*])
        :where [[post :post/title]]})))

  (let [{:keys [biff/db] :as sys} (get-sys)]
    (q db
      '{:find [id title body email time]
        :keys [post-id post-title post-body author post-time]
        :where [[id :post/title title]
                [id :post/body body]
                [id :post/created time]
                [id :post/user author-id]
                [author-id :user/email email]]
        :order-by [[time :desc]]}))
  
  (let [{:keys [biff/db]} (get-sys)
        post-id (parse-uuid "3bf0192c-3c09-429a-858e-fdf033292064")]
    (q db
      '{:find [title body]
        :in [post-id]
        :where [[post-id :post/title title]
                [post-id :post/body body]]}
      post-id))
  
  (let [{:keys [biff/db]} (get-sys)
        post-id (parse-uuid "3bf0192c-3c09-429a-858e-fdf033292064")
        [title body email] (first (q db 
                          '{:find [title body email]
                            :in [post-id]
                            :where [[post-id :post/title title]
                                    [post-id :post/body body]
                                    [post-id :post/user author]
                                    [author :user/email email]]}
                          post-id))]
    {:title title :body body :email email})
  
  (let [{:keys [biff/db]} (get-sys)
        id (parse-uuid "dbc80f25-f0f9-4cfd-8cd3-2826149faccf")]
    (xt/pull db 
      [:xt/id :post/title :post/body {:post/user [:user/email]} :post/time]
      id))
  
  
  (let [{:post/keys [title body user] :keys [xt/id]} (let [{:keys [biff/db]} (get-sys)
        id (parse-uuid "dbc80f25-f0f9-4cfd-8cd3-2826149faccf")]
    (xt/pull db 
      [:xt/id :post/title :post/body {:post/user [:user/email]}]
      id))]
    (:user/email user))

  (sort (keys @biff/system)))
