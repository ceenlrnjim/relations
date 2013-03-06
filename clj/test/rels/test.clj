(ns rels.test
  (:use [rels])
  (:use [clojure.test]))

(def data1 [ {:a 1 :b 11 :c 111},
             {:a 2 :b 22 :c 222},
             {:a 3 :b 33 :c 333}])
(def data2 [ {:z 1 :d 1111 :e {:ea "abc" :eb 3}},
             {:z 1 :d 1112 :e {:ea "def" :eb 2}},
             {:z 3 :d 3111 :e {:ea "hij" :eb 1}},
             {:z 3 :d 3112 :e {:ea "xyz" :eb 123}} ])

(deftest test-joins
  (let [nlj (nested-loop-join data1 data2 [:a :z =])
        hj (hash-join data1 data2 [:a :z =])
        j (join data1 data2 [:a :z =])
        j2 (join data1 data2 [:a :e #(= %1 (:eb %2))])]
    (is (= nlj hj j))
    (is (= (count j) 4))
    (is (= (count j2) 3))
    (let [sample (first (filter #(= (:a %) 3) j2))]
      (is (= (:z sample) 1))
      (is (= (:ea (:e sample)) "abc")))))

(run-tests 'rels.test)
  
