; TODO: nest
(ns relsquery
  (:use [rels]))

(defn apply-if-specified
  "kfpairs is a sequence of key (to clauses) and function pairs.
  Function will be invoked if the specified key is provided in clauses.
  Each function should take two arguments - the result of the previous
  and the values associated with the key in clauses."
  [clauses & kfpairs]
  (let [steps (partition 2 kfpairs)]
    (reduce
      (fn [r [k f]]
        ; if the clause wasn't specified, do run the function - skip this step
        (let [clause-val (get clauses k)]
          (if clause-val (f r (get clauses k)) r)))
      []
      steps)))

(defn multi-append
  "here fns is a sequence of key values and functions of rows that return values for that key.
   e.g. [[:newcol #(+ (:colA %) (:colB %))]...]"
  [r fns]
  (append r
    (fn [row]
      (apply hash-map 
        (flatten (map (fn [[k f]] [k (f row)]) fns))))))

(defn project-append
  [r cols]
  (let [{kws false adds true} (group-by vector? cols)]
    (project (multi-append r adds) (concat kws (map first adds)))))

(defn implicit-join
  [rs]
  (reduce #(join %1 %2) rs))

(defn query
  "Combines various relational functions into something resembling a SQL query.
  :select [...] to specify projection to subset of keys
  :deriving [[kw1 fn1] [kw2 fn2]...] adds key kw1 with the result of fn1 etc.
  :from [...] to specify relations to be joined - note that join is either natural or cartesian product
  :where [pred pred...] functions used to filter results as in rels/select
  :order-by [...] sorts the result based on the values of the specified keys"
  [& clause-pairs]
  (let [clauses (apply hash-map clause-pairs)]
    (apply-if-specified clauses
      ; either natural join, or cartesian product and then filter in :where - simple, but potentially slow and memory inefficient
      :from (fn [_ rs] (implicit-join rs))
      :deriving (fn [r ps] (multi-append r ps))
      :where (fn [r fs] (select r (fn [row] (every? identity (map #(% row) fs)))))
      :order-by (fn [r cols] (sort-by (fn [row] (reduce #(conj %1 (get row %2)) [] cols)) r))
      :select (fn [r cols] (project r cols)))))
;  note that the order of operations in the pipeline reflects trade off between performance
;  and flexibility.  For example, doing derive before where means that we derive on all records
;  but we can filter based on the derived columns.  Deriving after 'where' means we only have to derive
;  values for the matches, but can't filter on those derived values.
;  Derive is a separate clause to allow filtering and ordering based on derived values since select comes last

