(ns cider.nrepl.middleware.default
  "Default handler for convenience, works around a flawed dependency mechanism for now"
  (:require [clojure.tools.nrepl.middleware :as mw :refer [set-descriptor!]]
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
             [trace :as trace]
             [exceptions :as exceptions]]
            [cider.nrepl.middleware.util.cljs :as cljs]))

(defmacro defcomposed-handler
  "Generates a handler that subsumes all the other handlers, takes a name,
a sequence of middleware symbols, and a descriptor-fn to transform the derived descriptor."
  [name mws descriptor-fn]
  (let [vars (map resolve mws)
        descriptors (map (comp ::mw/descriptor meta) vars)
        handles (into {} (mapcat :handles descriptors))]
    `(do (defn ~name
           [handler#]
           (-> handler# ~@vars))
         (set-descriptor!
          (var ~name)
          (~descriptor-fn
           {:handles ~handles})))))

(defn conj-set
  [s val]
  (set (conj s val)))

(defcomposed-handler wrap-default
  [info/wrap-info
   complete/wrap-complete
   stacktrace/wrap-stacktrace
   classpath/wrap-classpath
   inspect/wrap-inspect
   trace/wrap-trace
   exceptions/wrap-exceptions]
  (comp
   #(update-in % [:requires] conj-set #'session)
   #(update-in % [:expects] conj-set #'pr-values)
   cljs/maybe-piggieback))


(comment
  (defn dbg-middlewares
    [middlewares]
    (->> middlewares
         clojure.tools.nrepl.middleware/linearize-middleware-stack
         (map meta)
         (map :name)
         (clojure.pprint/pprint)))
  (dbg-middlewares (concat clojure.tools.nrepl.server/default-middlewares
                          [#'cider.nrepl.middleware.stacktrace/wrap-stacktrace
                           #'cider.nrepl.middleware.inspect/wrap-inspect
                           #'cider.nrepl.middleware.info/wrap-info
                           #'cider.nrepl.middleware.complete/wrap-complete
                           #'cider.nrepl.middleware.trace/wrap-trace
                           #'cider.nrepl.middleware.classpath/wrap-classpath
                           #'cider.nrepl.middleware.exceptions/wrap-exceptions
                           ]))

  (dbg-middlewares (concat clojure.tools.nrepl.server/default-middlewares
                           [#'cider.nrepl.middleware.default/wrap-default]))
  

 )

