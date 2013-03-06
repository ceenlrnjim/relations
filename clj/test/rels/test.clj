(ns rels.test
  (:use [rels])
  (:use [clojure.test]))

(def data1 [ {:a 1 :b 11 :c 111},
             {:a 2 :b 22 :c 222},
             {:a 3 :b 33 :c 333}])
(def data2 [ {:a 1 :d 1111},
             {:a 1 :d 1112},
             {:a 3 :d 3111},
             {:a 3 :d 3112} ])

(deftest test-joins
  (let [nlj (apply nested-loop-join data1 data2 (common-keys-conditions data1 data2))
        hj (apply hash-join data1 data2 (common-keys-conditions data1 data2))
        j (join data1 data2)]
    (is (= nlj hj j))
    (is (= (count j) 4))
    (println j)))

(run-tests 'rels.test)
  
