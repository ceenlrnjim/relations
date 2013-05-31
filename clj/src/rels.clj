(ns rels
  (:require [clojure.core.reducers :as r])
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
  (reduce (fn [bool [f a_key b_key]] (and bool (rows-satisfy a_key b_key f a b)))
          true
          conditions))

(defn common-keys-conditions
  [r s]
  (let [ri (first r)
        si (first s)
        common-keys (clojure.set/intersection (set (keys ri)) (set (keys si)))]
    (for [k common-keys] [= k k])))

(defn nested-loop-join
  "r and s are relations, conditions is a list of [r_key s_key (fn)]"
  [r s & conditions]
  (for [ri r si s :when (all-conditions-met conditions ri si)]
    (merge ri si))) ; TODO: need to handle matching keys that aren't part of the join and might have different values

(defn hash-join
  [r s & conditions]
  (let [r-join-cols (map second conditions)
        s-join-cols (map #(nth % 2) conditions)
        ; NB: column names can be different and therefore can't be in the hash
        hashfn (comp hash vals select-keys)
        ; using group-by as there may be multiple records that match each s record since join key might not be unique
        ; key for the row
        r-hashes (group-by #(hashfn % r-join-cols) r)]
    (reduce
      (fn [result row]
        (let [matching-r (get r-hashes (hashfn row s-join-cols))]
          (if (nil? matching-r) result
            (concat result (map #(merge % row) matching-r)))))
      []
      s)))

(defn join
  "r and s are relations (sequence of maps), c is a sequence of 2 or 3 item sequences with (op colR colS) or (colR colS)"
  [r s & c]
  (let [conditions (if (empty? c) (common-keys-conditions r s) c)]
    (if (every? #(= = (first %)) conditions)
        (apply hash-join r s conditions)
        (apply nested-loop-join r s conditions))))

(defn project
  "returns a sequence of maps containing only the specified keys"
  [r ks]
  (map #(select-keys % ks) r))

(defn select
  [r f]
    (r/foldcat (r/filter f r)))

(defn select-by-vals
  "returns a sequence of maps from the specified relation where each specified key/value pair
   has the same value in the map"
  [r & kvs]
  (select r
    (fn [t]
      (let [m (apply hash-map kvs)]
        (= (select-keys t (keys m)) m)))))

(defn select-single
  "returns either nil or the first row that satisfies predicate f"
  ([r f default] 
    (let [matches (select r f)]
      (if (empty? matches) default (first matches))))
  ([r default k v & kvs] 
    (let [matches (apply select-by-vals r (concat [k v] kvs))]
      (if (empty? matches) default (first matches)))))

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
  "Appends key/values returned from f for each row to the row.  appends may happen in parallel
  and order of the original r may not be maintained"
  [r f]
  (r/foldcat (r/map #(merge % (f %)) r)))

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

(defn- multi-project-row
  [row & projections]
  (map
    #(select-keys row %)
    projections))

(defn- append-row
  " result is sequence of projections, row-projections is matching sequence of values for that row"
  [result row-projections]
  (map #(conj %1 %2) result row-projections))

(defn multi-project
  [r & projections]
  (reduce
    (fn [result row]
      (append-row result (apply multi-project-row row projections)))
    (map (fn [_] []) projections)
  r))

(defn denormalize
  "Converts a number of lines with different values for cols to a single line with a collection
  containing all values for cols - r is the relation, n is the name for the aggregate property
  and cols are the list of columns to be collapsed"
  [r n & cols]
  (let [keycols (difference (set (keys (first r))) (set cols))
        grouped (group-by #(select-keys % keycols) r)]
    ; each unique key is now a record in the relation
    (map (fn [[k v]]
           ; convert the value of the new property to just the denormalized fields
           (assoc k n (map #(select-keys % cols) v)))
         grouped)))

(defn normalize
  [r k]
  (let [other-ks (remove #(= % k) (keys (first r)))]
    (flatten
      (reduce (fn [rel row]
                (let [rest-row (select-keys row other-ks)]
                  (concat rel (map #(merge % rest-row) (get row k)))))
              []
              r))))

; TODO: unjoin, projectInto, projectMultipleInto,
