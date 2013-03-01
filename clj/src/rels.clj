(ns rels
  (:require clojure.set))

;
; Notes: assuming sequence of maps - not assuming sets which would make 
;

(defn rows-satisfy
  "returns true if the value of f(a[a_key] b[b_key]) is true"
  [a_key b_key f a b]
  (and (contains? a a_key)
       (contains? b b_key)
       (f (get a a_key) (get b b_key))))

(defn all-conditions-met
  "returns true if all the conditions specified are met by a and b"
  [conditions a b]
  (reduce (fn [bool [a_key b_key f]] (and bool (rows-satisfy a_key b_key f a b)))
          true
          conditions))

(defn common-keys-conditions
  [r s]
  (let [ri (first r)
        si (first s)
        common-keys (clojure.set/intersection (set (keys ri)) (set (keys si)))]
    (for [k common-keys] [k k =])))

(defn join
  "r and s are relations, conditions is a list of [r_key s_key (fn)]"
  [r s & c]
  (let [conditions (if (empty? c) (common-keys-conditions r s) c)]
    ; nested loop join - look into improving with sort/merge or hash join
    (for [ri r si s :when (all-conditions-met conditions ri si)]
      (merge ri si)))) ; TODO: need to handle matching keys that aren't part of the join and might have different values

(defn project
  "returns a sequence of maps containing only the specified keys"
  [r ks]
  (map #(select-keys % ks) r))

(defn select
  [r f]
  (filter f r))

(defn key-rename
  "replaces key ok with key nk in m"
  [m ok nk]
  (dissoc (assoc m nk (get m ok)) ok))

; TODO: must be easier way
(defn rename-keys
  "for each from, to, from, to in ftp, replaces the key from with the key to in m"
  [m & ftp]
  (if (empty? ftp) m
    (let [[oldname newname & more] ftp]
      (key-rename (apply rename-keys (cons m more)) oldname newname))))

(defn rename
  "renames the specified keys in all records in relation r.
  ftp is a sequence of 'from' 'to' pairs"
  [r & ftp]
  (map #(apply rename-keys (cons % ftp)) r))

(defn update-where
  [r matchFn updateFn]
  (map #(if (matchFn %) (updateFn %) %) r))

(defn intersect
  [r s]
  (clojure.set/intersection (set r) (set s)))

(defn difference
  [r s]
  (clojure.set/difference (set r) (set s)))

(defn union
  [r s]
  (set (clojure.set/union r s)))

(defn col-seq
  "Returns a sequence of values for the specified key in the relation"
  [r k]
  (map #(get % k) r))

(defn aggregate-key
  "reduces the values in attribute k of relation r using function f and initial value i"
  [r k f initial]
  (reduce f initial (col-seq r k)))

(defn append
  "Appends key/values returned from f for each row to the row"
  [r f]
  (map #(merge % (f %)) r))

(defn distinct-rows
  [r]
  (set r))

(defn divide
  [r s]
  (let [unique-keys (clojure.set/difference (set (keys (first r))) (set (keys (first s))))
        r-prime (project r unique-keys)
        bad-rows (-> (join r-prime s)
                     (difference r)
                     (project unique-keys))]
    (difference r-prime bad-rows)))
