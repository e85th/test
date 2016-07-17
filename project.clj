(defproject e85th/test "0.1.0-SNAPSHOT"
  :description "Testing related utilities."
  :url "http://github.com/e85th/test"
  :license {:name "Apache License 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [ring/ring-mock "0.3.0"]
                 [cheshire "5.6.3"]]


  :source-paths ["src/clj"]
  :java-source-paths ["src/java"]

  :deploy-repositories [["releases" :clojars]
                        ["snapshots" :clojars]])
