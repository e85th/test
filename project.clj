(defproject e85th/test "0.1.6"
  :description "Testing related utilities."
  :url "http://github.com/e85th/test"
  :license {:name "Apache License 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}
  :dependencies [[org.clojure/clojure "1.9.0-alpha17" :scope "provided"]
                 [ring/ring-mock "0.3.0"]
                 [com.cognitect/transit-clj "0.8.300"]
                 [cheshire "5.7.1"]]


  :source-paths ["src/clj"]
  :java-source-paths ["src/java"]

  ;; only to quell lein-cljsbuild when using checkouts
  :cljsbuild {:builds []}

  :deploy-repositories [["releases"  {:sign-releases false :url "https://clojars.org/repo"}]
                        ["snapshots" {:sign-releases false :url "https://clojars.org/repo"}]])
