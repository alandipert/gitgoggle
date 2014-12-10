(set-env!
 :src-paths    #{"src"}
 :dependencies '[[org.clojure/clojure       "1.6.0" :scope "provided"]
                 [tailrecursion/boot-useful "0.1.3" :scope "test"]
                 [clj-http                  "1.0.1"]
                 [clj-time                  "0.8.0"]
                 [doric                     "0.9.0"]])

(require '[tailrecursion.boot-useful :refer :all]
         '[alandipert.gitgoggle      :refer [issues]])

(def +version+ "1.0.0")

(useful! +version+)

(task-options!
 pom  [:project     'alandipert/gitgoggle
       :version     +version+
       :description "A Github issues status view"
       :url         "https://github.com/alandipert/gitgoggle"
       :scm         {:url "https://github.com/alandipert/gitgoggle"}
       :license     {:name "Eclipse Public License"
                     :url  "http://www.eclipse.org/legal/epl-v10.html"}])
