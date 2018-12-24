(ns e85th.test.http
  (:require [e85th.test.util :as u]
            [ring.mock.request :as mock]
            [cognitect.transit :as transit]
            [io.pedestal.test :as test]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [cheshire.core :as json])
  (:import [java.io InputStream ByteArrayInputStream ByteArrayOutputStream]))


(def ^:private routes nil)

(def ^{:doc "Request auth header."}
  auth-header "authorization")

(def http-success-status-codes #{200 201 202 203 204})

(defn success?
  "Answers if the input status-code is a http success code."
  [status-code]
  (http-success-status-codes status-code))

(defn content-type->name
  [s]
  (let [s (str/lower-case (or s ""))]
    (cond
      (str/includes? s "application/json") :json
      (str/includes? s "application/edn")  :edn
      :else :other)))

(defn json?
  "Answers true if the content-type is application/json. Ignores case, handles nil."
  [content-type]
  (= :json (content-type->name content-type)))

(defn edn?
  "Answers true if the content-type is application/json. Ignores case, handles nil."
  [content-type]
  (= :edn (content-type->name content-type)))

(defn init!
  "Call this first before using any other functions."
  [{:keys [routes]}]
  (alter-var-root #'routes (constantly routes)))

(defn rm-auth-header
  "Removes the auth header if it exists."
  [request]
  (u/dissoc-in request [:headers auth-header]))

(defn add-auth-header
  "Adds an auth header if the auth-token-value is not nil."
  [request auth-token-name auth-token-value]
  (cond-> request
    auth-token-value (mock/header auth-header (format "%s %s" auth-token-name auth-token-value))))

(defn json-request
  "Creates and returns a new mock ring request map with content-type set to application/json."
  ([method uri]
   (json-request method uri {}))
  ([method uri params]
   (json-request method uri params {}))
  ([method uri params headers]
   (assert (keyword? method))
   (assert (string? uri))
   (assert (or (map? params)
               (sequential? params)))
   (assert (map? headers))
   (let [params (if (= :get method) params (json/generate-string params))]
     (-> (mock/request method uri params)
         (mock/content-type "application/json")
         (mock/header "Accept" "application/json")
         (update-in [:headers] merge headers)))))

(defn edn-request
  "Creates and returns a new mock ring request map with content-type set to application/edn."
  ([method uri]
   (edn-request method uri {}))
  ([method uri params]
   (edn-request method uri params {}))
  ([method uri params headers]
   (assert (keyword? method))
   (assert (string? uri))
   (assert (or (map? params)
               (sequential? params)))
   (assert (map? headers))
   (let [params (if (= :get method) params (pr-str params))]
     (-> (mock/request method uri params)
         (mock/content-type "application/edn")
         (mock/header "Accept" "application/edn")
         (update-in [:headers] merge headers)))))

(defn transit-encode
  [x]
  (let [out (ByteArrayOutputStream. 4096)
        writer (transit/writer out :json)]
    (transit/write writer x)
    (.toString out)))

(defn transit-decode
  [^String s]
  (let [in (ByteArrayInputStream. (.getBytes s))
        reader (transit/reader in :json)]
    (transit/read reader)))

(defn transit-request
  "Creates and returns a new mock ring request map with content-type set to application/edn."
  ([method uri]
   (transit-request method uri {}))
  ([method uri params]
   (transit-request method uri params {}))
  ([method uri params headers]
   (assert (keyword? method))
   (assert (string? uri))
   (assert (or (map? params)
               (sequential? params)))
   (assert (map? headers))
   (let [params (if (= :get method) params (transit-encode params))]
     (-> (mock/request method uri params)
         (mock/content-type "application/transit+json")
         (mock/header "Accept" "application/transit+json")
         (update-in [:headers] merge headers)))))

(defn json-response-body-as-edn
  "Parse the ring response's body and return it as a Clojure data structure."
  [{:keys [body] :as response}]
  ;; Check for InputStream because api-call uses the routes directly and serialization
  ;; doesn't happen in testing with undertow anyway
  (let [body (if (instance? InputStream body) (slurp body) body)]
    (json/parse-string body true)))

(defn edn-response-body-as-edn
  "Parse the ring response's body and return it as a Clojure data structure."
  [{:keys [body] :as response}]
  ;; Check for InputStream because api-call uses the routes directly and serialization
  ;; doesn't happen in testing with undertow anyway
  (let [body (if (instance? InputStream body) (slurp body) body)]
    (edn/read-string {:readers *data-readers*} body)))

(defn transit-response-body-as-edn
  "Parse the ring response's body and return it as a Clojure data structure."
  [{:keys [body] :as response}]
  ;; Check for InputStream because api-call uses the routes directly and serialization
  ;; doesn't happen in testing with undertow anyway
  (let [body (if (instance? InputStream body) (slurp body) body)]
    (transit-decode body)))

(def ^{:doc "Takes a ring json response and converts it to a tuple [status-code body headers]"}
  json-response->tuple
  (juxt :status json-response-body-as-edn :headers))

(def ^{:doc "Takes a ring edn response and converts it to a tuple [status-code body headers]"}
  edn-response->tuple
  (juxt :status edn-response-body-as-edn :headers))

(def ^{:doc "Takes a ring transit response and converts it to a tuple [status-code body headers]"}
  transit-response->tuple
  (juxt :status transit-response-body-as-edn :headers))

(defn make-api-caller
  "Returns a function that can be used to make api-calls with the request-modifier
   applied to a request right before it hits the endpoint. The response-processor
   is used to process the response. NB. The returned function has 4 arities.
   [request]
   [method :- s/Keyword uri :- s/Str]
   [method :- s/Keyword uri :- s/Str params :- {s/Keyword s/Any}]
   [method :- s/Keyword uri :- s/Str params :- {s/Keyword s/Any} headers :- {s/Keyword s/Any}]"
  [make-request-fn request-modifier response-processor]
  (fn f
    ([request]
     (response-processor (routes (request-modifier request))))
    ([method uri]
     (f (make-request-fn method uri)))
    ([method uri params]
     (f (make-request-fn method uri params)))
    ([method uri params headers]
     (f (make-request-fn method uri params headers)))))


(defn make-json-api-caller
  ([]
   (make-json-api-caller identity))
  ([request-modifier]
   (make-api-caller json-request request-modifier json-response->tuple)))

(defn make-edn-api-caller
  ([]
   (make-edn-api-caller identity))
  ([request-modifier]
   (make-api-caller edn-request request-modifier edn-response->tuple)))

(defn make-transit-api-caller
  ([]
   (make-transit-api-caller identity))
  ([request-modifier]
   (make-api-caller transit-request request-modifier transit-response->tuple)))


(defn pedestal-service-caller
  "Returns a function that accepts verb url params and headers"
  [base-headers serializer deserializer service-fn]
  (fn [verb url params headers]
    (let [body (some-> params serializer)
          resp (test/response-for service-fn verb url
                                  :body body :headers (merge base-headers headers))]
      (update resp :body (fnil deserializer "")))))

(defn json-pedestal-caller
  ([service-fn]
   (json-pedestal-caller service-fn {}))
  ([service-fn {:keys [key-fn] :or {key-fn keyword} :as opts}]
   (pedestal-service-caller
    {"Accept" "application/json"
     "Content-Type" "application/json"}
    json/generate-string
    #(json/parse-string % key-fn)
    service-fn)))


(defn edn-pedestal-caller
  [service-fn]
  (pedestal-service-caller
   {"Accept" "application/edn"
    "Content-Type" "application/edn"}
   pr-str
   #(edn/read-string {:readers *data-readers*} %)
   service-fn))
