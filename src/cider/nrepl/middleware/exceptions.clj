(ns cider.nrepl.middleware.exceptions
  "Cause and stacktrace analysis for exceptions"
  (:require [clojure.pprint :as pp]
            [clojure.repl :as repl]
            [clojure.string :as str]
            [clojure.tools.nrepl.middleware :refer [set-descriptor!]]
            [clojure.tools.nrepl.middleware.pr-values :refer [pr-values]]
            [clojure.tools.nrepl.middleware.session :refer [session]]
            [clojure.tools.nrepl.misc :refer [response-for]]
            [clojure.tools.nrepl.transport :as t]
            [cider.nrepl.middleware
             [info :as info]
             [complete :as complete]
             [stacktrace :as stacktrace]
             [classpath :as classpath]
             [inspect :as inspect]
             [trace :as trace]]
            [cider.nrepl.middleware.util.misc :as u]))

(defn set-*e
  [{:keys [transport session] :as msg} e]
  (t/send transport (response-for msg {:status :error
                                       :ex (-> e class str)
                                       :root-ex (-> (#'clojure.main/root-cause e) class str)}))
  (swap! session assoc #'*e e)
  session)

(defn do-wrap-exceptions
  [handler {:keys [op session transport] :as msg}]
  (try (handler msg)
       (catch Exception e
         ;; set it on the (assumed-to-be tooling) session, mimicking interruptible-eval,
         ;; cider's error handler will be able to call back in and
         ;; get a pretty stacktrace via the stacktrace middleware
         (set-*e msg e)
         (t/send transport (response-for msg {:status :done})))))

(defn wrap-exceptions
  "Middleware that handles exceptions in a standardized way, sending cause and stack frame
  info for the most recent exception."
  [handler]
  (fn [msg]
    (do-wrap-exceptions handler msg)))

(set-descriptor!
 #'wrap-exceptions
 {:requires #{#'session}
  :expects #{#'pr-values "complete" "info"}
  :handles {}})

;; 
;; (alter-var-root #'*err* (constantly *out*))


