(ns com.example.feat.app
  (:require
    [com.example.middleware :as mid]
    [com.biffweb :as biff]
    [xtdb.api :as xt]))

;;=== DB ===
(defn get-posts 
  [db]
  (biff/q db 
    '{:find [id title body email time]
      :keys [post-id post-title post-body post-author post-time]
      :where [[id :post/title title]
              [id :post/body body]
              [id :post/created time]
              [id :post/user author-id]
              [author-id :user/email email]]
      :order-by [[time :desc]]}))

(defn get-post
  [id node]
  (let [[title body author time] (first (biff/q (xt/db node)
                                          '{:find [title body email time]
                                            :in [id]
                                            :where [[id :post/title title]
                                                    [id :post/body body]
                                                    [id :post/created time]
                                                    [id :post/user author-id]
                                                    [author-id :user/email email]]}
                                          (parse-uuid id)))]
    {:post-id id 
     :post-title title 
     :post-body body 
     :post-author author 
     :post-time time}))

(defn update-post!
  [id title body req]
  (biff/submit-tx req
    [{:db/doc-type :post
      :db/op :update
      :xt/id (parse-uuid id)
      :post/title title
      :post/body body}]))

(defn delete-post!
  [id req]
  (biff/submit-tx req 
    [{:db/doc-type :post
      :db/op :delete
      :xt/id (parse-uuid id)}]))

(defn create-post!
  [uid title body req]
  (biff/submit-tx req
    [{:db/doc-type :post
      :post/user uid
      :post/title title 
      :post/body body 
      :post/created :db/now}]))

;;=== Templates ==
(defn base
  [{:keys [title email]} & contents]
  [:html
   [:head
    [:meta {:charset "UTF-8"}]
    [:title (str title " - Biffr")]
    [:link {:href "/css/flaskr.css"
            :rel "stylesheet"}]
    [:script {:src "https://unpkg.com/htmx.org@1.8.0" 
              :integrity "sha384-cZuAZ+ZbwkNRnrKi05G/fjBX+azI9DNOkNYysZ0I/X5ZFgsmMiBXgDZof30F5ofc"
              :crossorigin "anonymous"}]
    [:script {:src "https://unpkg.com/hyperscript.org@0.9.7"}]]
   [:body
    [:nav
     [:h1 [:a {:href "/"} "Biffr"]]
     (if email 
       [:ul
        [:li [:span email]]
        [:li 
         (biff/form
           {:action "/auth/signout"}
           [:button {:type "submit"} "Log Out"])]]
       [:ul 
        [:li [:a {:hx-get "/login"
                  :hx-target ".content"
                  :hx-swap "innerHTML"} "Log In"]]])]
    [:section {:class "content"}
     contents]]])

(defn update-form
  [{:keys [id title body]}]
  [:div {:id (str "post-" id)} 
   (biff/form
     {:hx-put (str "/app/update/" id)
      :hx-target (str "#post-" id)
      :hx-swap "outerHTML"}
     [:label {:for "title"} "Title"]
     [:input {:name "title"
              :id "title"
              :value title
              :required true}]
     [:label {:for "body"} "Body"]
     [:textarea {:name "body"
                 :id "body"
                 :value body}]
     [:input {:type "submit"
              :value "Save"}])
   [:hr]
   (biff/form 
     {:hx-delete (str "/app/update/" id)
      :hx-confirm "Are you sure you wish to delete this post?" 
      :_ (str "on htmx:afterOnLoad remove #post-" id)}
     [:input.danger {:type "submit" :value "Delete"}])])

(defn render-post
  [{:keys [post-id post-title post-body post-author post-time]} email]
  [:article.post {:id (str "post-" post-id)}
   [:header
    [:div
     [:h1 post-title]
     [:div.about (str "by " (first (clojure.string/split post-author #"@")) " on " post-time)]]
    (if email 
      [:a.action {:hx-get (str "/app/update/" post-id)
                  :hx-target (str "#post-" post-id)
                  :hx-swap "outerHTML"} "Edit"])]
   [:p.body post-body]])

(defn index
  [{:keys [email posts]}]
  (base
    {:title "Posts"
     :email email}
    [:header
     [:h1 "Posts"]
     (if email 
       [:a.action {:hx-get "/app/create"
                   :hx-target "header"
                   :hx-swap "outerHTML"} "New"])]
    (for [post posts]
      (render-post post email))))

(defn login-form
  [] 
  (biff/form
    {:id "signin-form"
     :action "/auth/send"}
    [:label {:for "email"} "Email address:"]
    [:input {:name "email"
             :type "email"
             :autocomplete "email"
             :placeholder "Enter your email address"}]
    [:input {:type "submit"
             :value "Log In"}]))

(defn create-form
  [] 
  [:div 
   [:h1 "New Post"]
   (biff/form 
     {:action "/app/create"}
     [:label {:for "title"} "Title"]
     [:input {:name "title"
              :id "title"
              :placeholder "Enter post title here!"
              :required true}]
     [:label {:for "body"} "Body"]
     [:textarea {:name "body"
                 :id "body"
                 :placeholder "Enter post content here!"
                 :required true}]
     [:input {:type "submit" :value "Save"}])])

;; == Handler ==
(defn app
  [{:keys [session biff/db] :as req}]
  (let [{:user/keys [email]} (xt/entity db (:uid session))
        posts (get-posts db)]    
    (biff/render (index {:email email
                         :posts posts}))))

(defn login
  [_]
  (biff/render (login-form)))

(defn get-update-form
  [{:keys [path-params biff/db] :as req}]
  (let [id (:id path-params)
        [title body] (first (biff/q db 
                              '{:find [title body]
                                :in [post-id]
                                :where [[post-id :post/title title]
                                        [post-id :post/body body]]}
                              (parse-uuid id)))]
    (biff/render (update-form {:id id :title title :body body}))))

(defn update-post
  [{:keys [path-params params session biff/db biff.xtdb/node] :as req}]
  (let [id (:id path-params)
        title (:title params)
        body (:body params)
        {:user/keys [email]} (xt/entity db (:uid session))]
    (update-post! id title body req)
    (render-post (get-post id node) email)))

(defn delete-post
  [{:keys [path-params] :as req}]
  (delete-post! (:id path-params) req)
  {:status 204})

(defn get-create-form
  [_]
  (biff/render (create-form)))

(defn create-post
  [{:keys [params session] :as req}]
  (let [title (:title params)
        body (:body params)
        uid (:uid session)]
    (create-post! uid title body req))
  {:status 303
   :headers {"location" "/"}})

(defn home
  [_]
  {:status 303
   :headers {"location" "/"}})

(def features
  {:routes [""
            ["/" {:get app}]
            ["/login" {:get login}]
            ["/app" {:middleware [mid/wrap-signed-in]}
             ["/update/:id" {:get get-update-form
                             :put update-post
                             :delete delete-post}]
             ["/create/" {:get get-create-form
                          :post create-post}]]]})