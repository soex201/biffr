(ns com.example.schema
  (:require [malli.core :as malc]
            [malli.registry :as malr]))

(def schema
  {:user/id :uuid
   :user/email :string
   :user [:map {:closed true}
          [:xt/id :user/id]
          :user/email]

   :post/id :uuid
   :post/user :user/id
   :post/title :string
   :post/body :string
   :post/created inst?
   :post [:map {:closed true}
         [:xt/id :post/id]
         :post/user
         :post/title
         :post/body
         :post/created]})

(def malli-opts {:registry (malr/composite-registry malc/default-registry schema)})
