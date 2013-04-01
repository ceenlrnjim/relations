; TODO: nest
(ns relquery
  (:use [rels]))

(defn pipeline
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
    
(defn query
  [& clause-pairs]
  ;  TODO: will probably need a separate clause for join conditions
  ;  For now, starting with natural join or cartesian product - whichever applies to the relations
  ;  passed in
  ;
  ;  TODO: note that the order of operations in the pipeline reflects trade off between performance
  ;  and flexibility.  For example, doing derive before where means that we derive on all values
  ;  but we can filter based on the derived columns.  Deriving after 'where' means we only have to derive
  ;  values for the matches, but can't filter on those derived values.
  ;  Derive is a separate clause to allow filtering and ordering based on derived values
  (let [clauses (apply hash-map clause-pairs)]
    (pipeline clauses
      ; this one is required
      :from (fn [_ rs] (reduce #(join %1 %2) rs))
      :derive (fn [r ps] (multi-append r ps))
      :where (fn [r fs] (select r (fn [row] (every? identity (map #(% row) fs)))))
      :order-by (fn [r cols] (sort-by (fn [row] (reduce #(conj %1 (get row %2)) [] cols)) r))
      :select (fn [r cols] (project r cols)))))

; NB: since clause values are actual data structures, we could embed queries in the from clause like
; SQL implicit views
;(query :select [:a :z :d :e] :from [data1 data2] :where [#(= (:z %) (:a %))] :order-by [:d])
;
;
;(query :select [:a
;                :z
;                :d
;                [:q #(+ (:e %) (:z %))]
;         
