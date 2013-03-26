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
  (let [nlj (nested-loop-join data1 data2 [= :a :z])
        hj (hash-join data1 data2 [= :a :z])
        j (join data1 data2 [= :a :z])
        j2 (join data1 data2 [#(= %1 (:eb %2)) :a :e])]
    (is (= nlj hj j))
    (is (= (count j) 4))
    (is (= (count j2) 3))
    (let [sample (first (filter #(= (:a %) 3) j2))]
      (is (= (:z sample) 1))
      (is (= (:ea (:e sample)) "abc")))))

(def s [ {:score 1 :prodid :a :reqtid :1 }
         {:score 1 :prodid :a :reqtid :2 }
         {:score 1 :prodid :a :reqtid :3 }
         {:score 1 :prodid :a :reqtid :4 }
         {:score 2 :prodid :b :reqtid :1 }
         {:score 2 :prodid :b :reqtid :2 }
         {:score 2 :prodid :b :reqtid :3 }
         {:score 2 :prodid :b :reqtid :4 }
         {:score 3 :prodid :c :reqtid :1 }
         {:score 3 :prodid :c :reqtid :2 }
         {:score 3 :prodid :c :reqtid :3 }
         {:score 3 :prodid :c :reqtid :4 }
         {:score 4 :prodid :d :reqtid :1 }
         {:score 4 :prodid :d :reqtid :2 }
         {:score 4 :prodid :d :reqtid :3 }
         {:score 4 :prodid :d :reqtid :4 }])

(def p [ {:prodid :a :proddesc "Product A" }
         {:prodid :b :proddesc "Product B" }
         {:prodid :c :proddesc "Product B" }
         {:prodid :d :proddesc "Product D" } ])

(deftest test-commutative-hash
  (println "Starting commutative test")
  (let [sp (hash-join s p [= :prodid :prodid])
        ps (hash-join p s [= :prodid :prodid])]
    (is (= (count sp) (count ps)))))

(deftest test-normalize
  (let [data [ {:a 1 :b 1 :c [{:d 11 :e 11} {:d 12 :e 12} {:d 13 :e 13}]}
               {:a 2 :b 2 :c [{:d 21 :e 21} {:d 22 :e 22} {:d 23 :e 23}]}
               {:a 3 :b 3 :c [{:d 31 :e 31} {:d 32 :e 32} {:d 33 :e 33}]} ]
        norm-data (normalize data :c)]
    (is (nil? (:c (first norm-data))))
    (is (not (nil? (:d (first norm-data)))))
    (is (= (count norm-data) 9))))

(run-tests 'rels.test)
  
