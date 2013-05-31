(ns rels.query.test
  (:use [relsquery])
  (:use [clojure.test]))

(def data1 [
  {:a 1 :b 11 :c 111}
  {:a 2 :b 22 :c 222}
  {:a 3 :b 33 :c 333}
  {:a 4 :b 44 :c 444}
  {:a 5 :b 55 :c 555}
])

(def data2 [
  {:a 1 :d 11 :e 99}
  {:a 1 :d 12 :e 1}
  {:a 2 :d 21}
  {:a 2 :d 22}
  {:a 3 :d 31}
  {:a 3 :d 32}
  {:a 4 :d 41}
  {:a 4 :d 42}
  {:a 5 :d 51}
  {:a 5 :d 52}
])

(def data3 [
  {:e 99 :desc "Number 99"}
  {:e 1 :desc "Number 1"}
])

(deftest two-table-join-only
  (let [result (query :from [data1 data2])]
    (is (= (count result) 10))))

(deftest join-project
  (let [result (query :select [:c :d] :from [data1 data2])]
    (println result)
    (is (= (count result) 10))
    (is (every? #(not (contains? % :a)) result))
    (is (every? #(not (contains? % :b)) result))
    (is (= (count (filter #(and (= (:d %) 42) (= (:c %) 444)) result)) 1))))

(deftest join-select
  (let [result1 (query :from [data1 data2] :where [#(= (:d %) 32)])
        result2 (query :from [data1 data2] :where [#(= (:d %) 32) #(= (:b %) 1)])]
    (is (= (count result1) 1))
    (is (= (count result2) 0))
    (is (= (:d (first result1)) 32))))

(deftest join-order
  (let [result (query :from [data1 data2] :where [#(= (:a %) 1)] :order-by [:a :e])
        record (first result)]
    (is (= (count result) 2))
    (is (= (:a record) 1))
    (is (= (:e record) 1))
    (is (= (:d record) 12))))

(deftest join-derive
  (println "join-derive -------------------------")
  (let [result (query :select [:a :d :q]
                      :from [data1 data2]
                      :deriving [[:q #(+ (:d %) (:c %))]]
                      :where [#(even? (:q %))]
                      )]
    (doseq [i result] (println i))
    ))
                
(deftest multi-join
  (let [result (query :from [data1 data2 data3])]
    ;(doseq [i result] (println i))
    ))

(deftest test-explicit-join
  (let [result (query :from [data1] :join [[data2 = :a :a], [data3 = :e :e]])]
    (doseq [i result] (println i))))


(run-tests 'rels.query.test)
