(ns calculator.web
  (:require [compojure.core :refer [defroutes GET PUT POST DELETE ANY]]
            [compojure.handler :refer [site]]
            [compojure.route :as route]
            [clojure.java.io :as io]
            [ring.middleware.stacktrace :as trace]
            [ring.middleware.session :as session]
            [ring.middleware.session.cookie :as cookie]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.basic-authentication :as basic]
            [cemerick.drawbridge :as drawbridge]
            [environ.core :refer [env]]))

(use '[clojure.string :only (join split)])


(defn- authenticated? [user pass]
  ;; TODO: heroku config:add REPL_USER=[...] REPL_PASSWORD=[...]
  (= [user pass] [(env :repl-user false) (env :repl-password false)]))

(def ^:private drawbridge
  (-> (drawbridge/ring-handler)
      (session/wrap-session)
      (basic/wrap-basic-authentication authenticated?)))


(defn make-operation [op, label]
  (fn [args]
      {:status 200
        :headers {"Content-Type" "text/plain"}
        :body (pr-str 
          [label 
            args
            "Result"
            (reduce op (map read-string (split args #",")))
          ])
      }
    )
  )


(defroutes app
  (ANY "/repl" {:as req}
       (drawbridge req))
  (GET "/" []
       {:status 200
        :headers {"Content-Type" "text/plain"}
        :body (pr-str "This is a calculator. Append add, subtract, multiply, or divide to this url, and supply 'args' as a GET param.")})
  (GET "/add" [args] ((make-operation + "Add") args))
  (GET "/subtract" [args] ((make-operation - "Subtract") args))
  (GET "/multiply" [args] ((make-operation * "Multiply") args))
  (GET "/divide" [args] ((make-operation / "Divide") args))
  (ANY "*" []
       (route/not-found (slurp (io/resource "404.html")))))


(defn wrap-error-page [handler]
  (fn [req]
    (try (handler req)
         (catch Exception e
           {:status 500
            :headers {"Content-Type" "text/html"}
            :body (slurp (io/resource "500.html"))}))))

(defn -main [& [port]]
  (let [port (Integer. (or port (env :port) 5000))
        ;; TODO: heroku config:add SESSION_SECRET=$RANDOM_16_CHARS
        store (cookie/cookie-store {:key (env :session-secret)})]
    (jetty/run-jetty (-> #'app
                         ((if (env :production)
                            wrap-error-page
                            trace/wrap-stacktrace))
                         (site {:session {:store store}}))
                     {:port port :join? false})))

;; For interactive development:
;; (.stop server)
;; (def server (-main))
