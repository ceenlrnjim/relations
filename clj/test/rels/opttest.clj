(ns optrels.test
  (:use [optrels])
  (:use [clojure.test]))

(comment
(def r #{{:a 1 :b 10 :c 100}{:a 2 :b 2 :c 200}{:a 3 :b 30 :c 300}})
(def s #{{:d 1 :name "foo"}{:d 2 :name "bar"}})
(def q #{{:d 1 :age 27}{:d 2 :age 506}})
)

(deftest multi-pushdown
  (let [data1 #{{:id 1 :name "foo"}}
        data2 #{{:parent-id 2 :child-id 2 :child-name "bar"}}
        theq (select * from data1 data2 where (and (= :id :parent-id) (= :child-id 50) (> :parent-id 900)))
        optq (optimize-expr (analyze theq))]
        (println theq)
        (println optq)
    (is (= :restrict (first optq)))
    (is (= '(= :id :parent-id) (second optq)))))
    
(run-tests 'optrels.test)
