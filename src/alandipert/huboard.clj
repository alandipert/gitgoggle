(ns alandipert.huboard
  {:boot/export-tasks true}
  (:require [clj-http.client :as    client]
            [clj-time.coerce :as    c]
            [clj-time.format :as    f]
            [boot.pod        :as    pod]
            [boot.util       :as    util]
            [boot.core       :as    core]
            [clojure.java.io :as    io]
            [boot.util       :refer [with-let]]
            [clojure.string  :as    str]
            [doric.core      :refer [table]]))

(def env-creds
  (delay (let [creds (mapv #(System/getenv %) ["GH_USER" "GH_PASS"])]
           (if (every? nil? creds)
             (throw (RuntimeException. "You must specify GH_USER and GH_PASS"))
             creds))))

(defn orgs []
  (-> "https://api.github.com/user/orgs"
      (client/get {:basic-auth @env-creds :as :json})
      :body))

(defn org-repos [org]
  (-> (format "https://api.github.com/orgs/%s/repos" org)
      (client/get {:basic-auth @env-creds :as :json})
      :body))

(defn coerce-dates [record]
  (reduce #(update-in %1 [%2] c/from-string) record [:created_at :updated_at]))

(defn format-date [issue]
  (assoc issue :date (f/unparse (:year-month-day f/formatters) (:updated_at issue))))

(defn short-title [issue]
  (assoc issue :short-title (let [title (:title issue)]
                              (if (> (.length title) 30)
                                (str (subs (:title issue) 0 30) "...")
                                title))))

(defn repo-issues [org repo]
  (for [issue (-> (format "https://api.github.com/repos/%s/%s/issues" org repo)
                  (client/get {:basic-auth @env-creds :as :json})
                  :body)]
    (-> (coerce-dates issue)
        format-date
        (assoc :repo repo)
        short-title)))

(defn print-issues [issues]
  (println (table [{:title "Updated" :name :date}
                   {:title "Repo"    :name :repo}
                   {:title "Issue"   :name :short-title}
                   {:title "URL"     :name :html_url}]
                  (reverse (sort-by (comp c/to-date :updated_at) issues)))))

(core/deftask issues
  "Displays issues GitHub repositories."
  [o org   ORG  str "The GitHub organization"
   r repos REPO #{str} "The repositories to show issues for (default is all)"]
  (core/with-pre-wrap
    (if org
      (let [repos (or (seq repos) (map :name (org-repos org)))]
        (print-issues (mapcat #(repo-issues org %) repos)))
      (throw (IllegalArgumentException. "Must specify a repository")))))
