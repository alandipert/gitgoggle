(ns alandipert.gitgoggle
  {:boot/export-tasks true}
  (:require [clj-http.client       :as    client]
            [clj-time.coerce       :as    c]
            [clj-time.format       :as    f]
            [boot.pod              :as    pod]
            [boot.util             :as    util]
            [boot.core             :as    core]
            [clojure.java.io       :as    io]
            [boot.util             :refer [with-let]]
            [clojure.string        :as    str]
            [doric.core            :as    doric]))

(declare ^:dynamic *token*)
(def     ^:dynamic *http-quiet* false)
(def     ^:dynamic *long-title* true)

(let [out (agent nil)]
  (defn get* [url]
    (binding [*out* *err*]
      (if-not *http-quiet* (send-off out (fn [_] (println (str "GET " url "...")))))
      (:body (client/get url {:query-params {"access_token" *token*} :as :json})))))

(defn orgs []
  (get* "https://api.github.com/user/orgs"))

(defn org-repos [org]
  (get* (format "https://api.github.com/orgs/%s/repos" org)))

(defn user-repos [org]
  (get* (format "https://api.github.com/user/repos" org)))

(defn coerce-dates [record]
  (reduce #(update-in %1 [%2] c/from-string) record [:created_at :updated_at]))

(defn format-date [issue]
  (assoc issue :date (f/unparse (:year-month-day f/formatters) (:updated_at issue))))

(defn short-title [issue]
  (if *long-title*
    issue
    (assoc issue :short-title (let [title (:title issue)]
                                (if (> (.length title) 30)
                                  (str (subs (:title issue) 0 30) "...")
                                  title)))))

(defn repo-issues [org repo]
  (for [issue (get* (format "https://api.github.com/repos/%s/%s/issues" org repo))]
    (-> (coerce-dates issue)
        format-date
        (assoc :repo repo)
        short-title)))

(defn issues-table [doric-format issues]
  (doric/table
   {:format doric-format}
   [{:title "Updated" :name :date}
    {:title "Repo"    :name :repo}
    {:title "Issue"   :name (if *long-title* :title :short-title)}
    {:title "URL"     :name :html_url}]
   (reverse (sort-by (comp c/to-date :updated_at) issues))))

(def token-message
  "Must set GITHUB_TOKEN or supply --token.  See
  https://help.github.com/articles/creating-an-access-token-for-command-line-use/
  for instructions on how to create one.")

(defn get-format [s default]
  (if (nil? s)
    default
    (try
      @(ns-resolve (the-ns 'doric.core) (symbol s))
      (catch NullPointerException e
        (throw (IllegalArgumentException. (str s " is not a supported format.")))))))

(core/deftask issues
  "Shows open issues for GitHub repositories."
  [o org    ORG    str    "The GitHub organization"
   u user          bool   "Query user repositories instead of an organization."
   r repos  REPO   #{str} "The repositories to show issues for (default is all)"
   t token         str    "The GitHub access_token; you can also set GITHUB_TOKEN.  See http://bit.ly/1x1AEVb"
   f format FORMAT str    "The table format.  Available formats are: csv html org raw."
   q quiet         bool   "Suppress HTTP client activity output."
   l long-title    bool   "Don't truncate issue titles, false by default."]
  (core/with-pre-wrap
    (if org
      (binding [*token*       (or token
                                  (System/getenv "GITHUB_TOKEN")
                                  (throw (RuntimeException. token-message)))
                *http-quiet*  quiet
                *long-title*  long-title]
        (let [issues (->> (or (seq repos) (map :name (org-repos org)))
                          (pmap #(repo-issues org %))
                          (mapcat identity))]
          (if (seq issues)
            (println (issues-table (get-format format doric/org) issues))
            (binding [*out* *err*]
              (println "No issues to display.")))))
      (throw (IllegalArgumentException. "Must specify org")))))
