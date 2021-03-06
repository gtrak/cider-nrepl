(ns cider.nrepl.middleware.track-state-test
  (:require [cider.nrepl.middleware.track-state :as s]
            [cider.nrepl.middleware.util.cljs :as cljs]
            [clojure.test :refer :all]
            [clojure.tools.nrepl.transport :as t])
  (:import clojure.tools.nrepl.transport.Transport))

(def ^:const msg {:session :dummy})

(deftest make-transport
  (is (instance? Transport (s/make-transport msg)))
  (is (try (send (s/make-transport msg) 10)
           nil
           (catch Exception e true))))

(deftest track-ns?
  (is (not (some s/track-ns? s/jar-namespaces))))

(deftest update-and-send-cache
  (let [sent-value (atom nil)]
    (with-redefs [s/track-ns? (constantly true)
                  t/send (fn [t m] (reset! sent-value m))]
      (let [new-data (s/update-and-send-cache nil msg)]
        (is (map? new-data))
        (is (> (count new-data) 100)))
      (let [{:keys [repl-type changed-namespaces]} @sent-value]
        (is (= repl-type :clj))
        (is (map? changed-namespaces))
        (is (> (count changed-namespaces) 100)))
      (let [full-cache (s/update-and-send-cache nil msg)
            get-sent-value (fn [old] (s/update-and-send-cache old msg)
                             @sent-value)]
        ;; Return value depends only on the current state.
        (is (= (s/update-and-send-cache nil msg)
               (s/update-and-send-cache (into {} (take 5 full-cache)) msg)
               (s/update-and-send-cache full-cache msg)))
        ;; Sent message depends on the first arg.
        (is (= (get-sent-value full-cache)
               (get-sent-value full-cache)))
        (is (= (get-sent-value (into {} (drop 3 full-cache)))
               (get-sent-value (into {} (drop 3 full-cache))))))
      ;; In particular, the sent message only contains the diff.
      (let [changed-again (:changed-namespaces @sent-value)]
        (is (map? changed-again))
        (is (= (count changed-again) 3)))
      ;; Check repl-type :cljs
      (with-redefs [cljs/grab-cljs-env (constantly true)]
        (s/update-and-send-cache nil msg)
        (let [{:keys [repl-type changed-namespaces]} @sent-value]
          (is (= repl-type :cljs))
          (is (map? changed-namespaces)))))))

(deftest update-vals
  (is (= (s/update-vals inc {1 2 3 4 5 6})
         {1 3 3 5 5 7}))
  (is (= (s/update-vals range {1 2 3 4 5 6})
         '{5 (0 1 2 3 4 5), 3 (0 1 2 3), 1 (0 1)}))
  (is (= (s/update-vals str {:a :b :c :d :e :f})
         {:e ":f", :c ":d", :a ":b"}))
  (is (= (s/update-vals odd? {1 2 3 4 5 6})
         {1 false 3 false 5 false})))

(deftest filter-core-and-get-meta
  (is (= (s/filter-core-and-get-meta {'and #'and, 'b #'map, 'c #'deftest})
         '{c {:macro "true", :arglists "([name & body])"}}))
  (is (-> (find-ns 'clojure.core)
          ns-map s/filter-core-and-get-meta
          seq not)))

(deftest relevant-meta
  (is (= (:macro (s/relevant-meta (meta #'deftest)))
         "true"))
  (alter-meta! #'update-vals merge {:indent 1 :cider-instrumented 2 :something-else 3})
  (is (= (s/relevant-meta (meta #'update-vals))
         {:cider-instrumented "2", :indent "1",
          :test (pr-str (:test (meta #'update-vals)))})))

(deftest ns-as-map
  (alter-meta! #'update-vals
               merge {:indent 1 :cider-instrumented 2 :something-else 3})
  (let [{:keys [interns aliases] :as ns} (s/ns-as-map (find-ns 'cider.nrepl.middleware.track-state-test))]
    (is (> (count interns) 5))
    (is (map? interns))
    (is (interns 'ns-as-map))
    (is (:test (interns 'ns-as-map)))
    (is (= (into #{} (keys (interns 'update-vals)))
           #{:cider-instrumented :indent :test}))
    (is (> (count aliases) 2))
    (is (= (aliases 's)
           'cider.nrepl.middleware.track-state)))
  (with-redefs [s/track-ns? (constantly nil)]
    (let [{:keys [interns aliases] :as ns}
          (s/ns-as-map (find-ns 'cider.nrepl.middleware.track-state-test))]
      (is interns))))

(deftest calculate-used-aliases
  (let [nsm {'cider.nrepl.middleware.track-state-test
             (s/ns-as-map (find-ns 'cider.nrepl.middleware.track-state-test))}]
    (is (contains? (into #{} (keys (s/calculate-used-aliases nsm nil)))
                   'cider.nrepl.middleware.track-state))
    (is (contains? (into #{} (keys (s/calculate-used-aliases nsm {'cider.nrepl.middleware.track-state nil})))
                   'cider.nrepl.middleware.track-state))
    (is (contains? (into #{} (keys (s/calculate-used-aliases (assoc nsm 'cider.nrepl.middleware.track-state nil) nil)))
                   'cider.nrepl.middleware.track-state))))
