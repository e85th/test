(ns e85th.test.http
  (:require [e85th.test.util :as u]
            [ring.mock.request :as mock]
            [cheshire.core :as json])
  (:import [java.io InputStream]))


(def ^:private routes nil)

(def ^{:doc "Request auth header."}
  auth-header "authorization")

(defn init!
  "Call this first before using any other functions."
  [{:keys [routes]}]
  (alter-var-root #'routes (constantly routes)))

(defn json-request
  "Creates and returns a new mock ring request map with content-type set to application/json."
  ([method uri]
   (json-request method uri {}))

  ([method uri params]
   (assert (keyword? method))
   (assert (string? uri))
   (assert (map? params))
   (let [params (if (= :get method) params (json/generate-string params))]
     (-> (mock/request method uri params)
         (mock/content-type "application/json")))))

(defn json-response-body-as-edn
  "Parse the ring response's body and return it as a Clojure data structure."
  [{:keys [body] :as response}]
  ;; Check for InputStream because api-call uses the routes directly and serialization
  ;; doesn't happen in testing with undertow anyway
  (let [body (if (instance? InputStream body) (slurp body) body)]
    (json/parse-string body true)))

(defn rm-auth-header
  "Removes the auth header if it exists."
  [request]
  (u/dissoc-in request [:headers auth-header]))

(defn add-auth-header
  "Adds an auth header if the auth-token-value is not nil."
  [request auth-token-name auth-token-value]
  (cond-> request
    auth-token-value (mock/header auth-header (format "%s %s" auth-token-name auth-token-value))))

(def ^{:doc "Takes a ring response and converts it to a tuple [status-code body headers]"}
  response->tuple
  (juxt :status json-response-body-as-edn :headers))

(defn make-api-caller
  "Returns a function that can be used to make api-calls with the request-modifier
   applied to a request right before it hits the endpoint. The response-processor
   is used to process the response. NB. The returned function has 3 arities.
   [request]
   [method :- s/Keyword uri :- s/Str]
   [method :- s/Keyword uri :- s/Str params :- {s/Keyword s/Any}]"
  ([request-modifier]
   (make-api-caller request-modifier response->tuple))

  ([request-modifier response-processor]
   (fn f
     ([request]
      (response-processor (routes (request-modifier request))))
     ([method uri]
      (f (json-request method uri)))
     ([method uri params]
      (f (json-request method uri params))))))

(def ^{:doc "Calls an endpoint and returns the .
             The status-code is an int, response-body is a Clojure data structure.
             Executes a json request to the api endpoint.
             eg: (api-call :get \"/foo\" {:a 1 :b 2})"}
  api-call (make-api-caller identity))
