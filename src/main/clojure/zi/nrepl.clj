(ns zi.nrepl
  "nREPL mojo for zi plugin"
  (:require
   [zi.mojo :as mojo]
   [zi.core :as core]
   [zi.checkouts :as checkouts]
   [clojure.java.io :as io])
  (:use
   [zi.maven :only [resolve-dependency]]
   [clojure.tools.nrepl.server :only (start-server stop-server)])
  (:import
   java.io.File
   [clojure.maven.annotations
    Goal RequiresDependencyResolution Parameter Component]
   org.apache.maven.plugin.MojoExecutionException))


(mojo/defmojo Nrepl
  {Goal "nrepl"
   RequiresDependencyResolution "test"}
  [^{Component {:role "org.sonatype.aether.RepositorySystem"
                :alias "repoSystem"}}
   repo-system

   ^{Component {:role "org.apache.maven.project.ProjectBuilder"
                :alias "projectBuilder"}}
   project-builder

   ^{Parameter {:defaultValue "${repositorySystemSession}" :readonly true
                :alias "repoSystemSession"}}
   repo-system-session

   ^{Parameter
     {:expression "${clojure.nrepl.port}" :defaultValue "4005"
      :description "nREPL server port"}}
   ^Integer
   port

   ^{Parameter
     {:expression "${clojure.nrepl.bind}" :defaultValue "localhost"
      :description "Network address for the server to bind to"}}
   ^String
   bind

   ^{Parameter
     {:expression "${project}"
      :description "Project"}}
   project]

  (let [nrepl-artifacts (resolve-dependency
                         repo-system
                         repo-system-session
                         (.getRemoteProjectRepositories project)
                         "org.clojure" "tools.nrepl"
                         (or (System/getProperty "nrepl.version")
                             "0.2.0-beta9")
                         {})]
    (core/eval-clojure
     (into (core/clojure-source-paths source-directory)
           (checkouts/checkout-paths
            repo-system repo-system-session project-builder))
     (concat test-classpath-elements nrepl-artifacts)
     `(do
        (require '~'clojure.tools.nrepl.server)
        (clojure.tools.nrepl.server/start-server :port ~(Integer/parseInt port) :bind ~bind)))))


(comment
  (deftype
      ^{Goal "nrepl"
        RequiresDependencyResolution "test"}
      NreplMojo
    [
     ^{Parameter {:typename "java.lang.String[]"}}
     sourceDirectories

     ^{Parameter
       {:expression "${clojure.nrepl.port}" :defaultValue "7888"}}
     ^Integer
     port

     ^{:volatile-mutable true}
     log

     plugin-context
     ]

    Mojo
    (execute [_]
      (.info log sourceDirectories))

    (setLog [_ logger] (set! log logger))
    (getLog [_] log)

    ContextEnabled
    (setPluginContext [_ context] (reset! plugin-context context))
    (getPluginContext [_] @plugin-context))

  (defn make-SimpleMojo
    []
    (SimpleMojo. nil nil (atom nil))))

;; starting an nrepl server. via https://github.com/clojure/tools.nrepl
;; => (use '[clojure.tools.nrepl.server :only (start-server stop-server)])
;; nil
;; => (defonce server (start-server :port 7888))
;; #'user/server
;; You can stop the server with (stop-server server)

;; from lein
;; (defn- start-server [project host port ack-port & [headless?]]
;;   (let [server-starting-form
;;         `(let [server# (clojure.tools.nrepl.server/start-server
;;                         :bind ~host
;;                         :port ~port
;;                         :ack-port ~ack-port
;;                         :handler ~(handler-for project))
;;                port# (-> server# deref :ss .getLocalPort)]
;;            (println "nREPL server started on port" port#)
;;            (spit ~(str (io/file (:target-path project) "repl-port")) port#)
;;            @(promise))]
;;     (if project
;;       (eval/eval-in-project
;;        (project/merge-profiles project [(:repl (user/profiles) profile)
;;                                         (if-not headless? reply-profile)])
;;        server-starting-form
;;        `(require ~@(init-requires project)))
;;       (eval server-starting-form))))
