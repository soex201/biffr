(ns com.example.feat.app
  (:require
    [com.example.middleware :as mid]
    [com.biffweb :as biff]
    [xtdb.api :as xt]
    [clojure.string :as str]))

;;=== DB ===
(defn get-posts 
  [db]
  (->> (biff/q db 
         '{:find (pull post [* {:post/user [*]}])
           :where [[post :post/title]]})
    (sort-by :post/created #(compare %2 %1))))

(defn get-post
  [id db]
  (xt/pull db 
    [:xt/id :post/title :post/body {:post/user [:user/email :xt/id]} :post/created]
    (parse-uuid id)))

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
  [{:post/keys [title body user created] :keys [xt/id]} email]
  (let [post-author (:user/email user)]
    [:article.post {:id (str "post-" id)}
     [:header
      [:div
       [:h1 title]
       [:div.about (str "by " (first (str/split post-author #"@")) " on " created)]]
      (when (= email post-author) 
        [:a.action {:hx-get (str "/app/update/" id)
                    :hx-target (str "#post-" id)
                    :hx-swap "outerHTML"} "Edit"])]
     [:p.body body]]))

(defn index
  [{:keys [email posts]}]
  (base
    {:title "Posts"
     :email email}
    [:header
     [:h1 "Posts"]
     (when email 
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
  [{:keys [session biff/db]}]
  (let [{:user/keys [email]} (xt/entity db (:uid session))
        posts (get-posts db)]    
    (index {:email email
            :posts posts})))

(defn login
  [_]
  (login-form))

(defn get-update-form
  [{:keys [path-params biff/db]}]
  (let [id (:id path-params)
        [title body] (first (biff/q db 
                              '{:find [title body]
                                :in [post-id]
                                :where [[post-id :post/title title]
                                        [post-id :post/body body]]}
                              (parse-uuid id)))]
    (update-form {:id id :title title :body body})))

(defn update-post
  [{:keys [path-params params session biff/db biff.xtdb/node] :as req}]
  (let [id (:id path-params)
        title (:title params)
        body (:body params)
        {:user/keys [email]} (xt/entity db (:uid session))]
    (update-post! id title body req)
    (render-post (get-post id (xt/db node)) email)))

(defn delete-post
  [{:keys [path-params] :as req}]
  (delete-post! (:id path-params) req)
  {:status 204})

(defn get-create-form
  [_]
  (create-form))

(defn create-post
  [{:keys [params session] :as req}]
  (let [title (:title params)
        body (:body params)
        uid (:uid session)]
    (create-post! uid title body req))
  {:status 303
   :headers {"location" "/"}})

(defn wrap-update [handler]
  (fn [{:keys [session path-params biff/db] :as req}]
    (let [id (:id path-params)
          {:post/keys [user]} (get-post id db)]
      (if (= (:xt/id user) (:uid session))
        (handler req)
        {:status 303
         :headers {"location" "/"}}))))

(def features
  {:routes [""
            ["/" {:get app}]
            ["/login" {:get login}]
            ["/app" {:middleware [mid/wrap-signed-in]}
             ["/update/:id" {:middleware [wrap-update]
                             :get get-update-form
                             :put update-post
                             :delete delete-post}]
             ["/create/" {:get get-create-form
                          :post create-post}]]]})