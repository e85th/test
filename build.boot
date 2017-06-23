(set-env!
 :resource-paths #{"src/clj"}
 :dependencies '[[org.clojure/clojure "1.9.0-alpha15" :scope "provided"]
                 [ring/ring-mock "0.3.0"]
                 [cheshire "5.7.1"]
                 [com.cognitect/transit-clj "0.8.300"]
                 [adzerk/boot-test "1.2.0" :scope "test"]]

 :repositories #(conj %
                      ["clojars" {:url "https://clojars.org/repo"
                                  :username (System/getenv "CLOJARS_USER")
                                  :password (System/getenv "CLOJARS_PASS")}]))

(require '[adzerk.boot-test :as boot-test])

(deftask test
  "Runs the unit-test task"
  []
  (comp
   (javac)
   (boot-test/test)))



(deftask build
  "Builds a jar for deployment."
  []
  (comp
   (javac)
   (pom)
   (jar)
   (target)))

(deftask dev
  "Starts the dev task."
  []
  (comp
   (repl)
   (watch)))

(deftask deploy
  []
  (comp
   (build)
   (push)))

(task-options!
 pom {:project 'e85th/test
      :version "0.1.5"
      :description "Testing related utilities."
      :url "http://github.com/e85th/test"
      :scm {:url "http://github.com/e85th/test"}
      :license {"Apache License 2.0" "http://www.apache.org/licenses/LICENSE-2.0"}}
 push {:repo "clojars"})
