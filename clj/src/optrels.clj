(ns optrels
  (:require [clojure.set :as sets])
  (:require [clojure.zip :as zip]))

; Note - don't support getting attributes for empty relations
(defn attrs [r]
  {:pre [(seq r)]}
  (set (keys (first r))))

(defn union-compatible? [r s]
  (or (not (seq r))
           (not (seq s))
           (= (attrs r) (attrs s))))

; relations are sets (lists? seqs?) of maps - all rows are assumed to have the same keys
; [:union R S]

(defn rel-union [r s]
  {:pre [(union-compatible? r s)]}
  (sets/union r s))

(defn rel-diff [r s]
  {:pre [(union-compatible? r s)]}
  (sets/difference r s))

(defn rel-intersect [r s]
  {:pre [(union-compatible? r s)]}
  (sets/intersection r s))

(defn- disjoint-headers? [r s]
  (not (seq (sets/intersection (attrs r) (attrs s)))))

(defn cartprod
  "Cartesian product of two relations"
  [r s]
  {:pre [(disjoint-headers? r s)]}
  (set (for [x r y s] (merge x y))))

(defn project [r ks]
  {:pre [(empty? (sets/difference ks (attrs r)))]} ; all ks are valid columns in r
  (set (map #(select-keys % ks) r)))

; stolen from http://www.ibm.com/developerworks/library/j-treevisit/
(defn- tree-edit [zipper matcher editor]
  (loop [loc zipper]
    (if (zip/end? loc)
      (zip/root loc)
      (if-let [matcher-result (matcher (zip/node loc))]
        (recur (zip/next (zip/edit loc (partial editor matcher-result))))
        (recur (zip/next loc))))))

(defn- tree-replace-keywords [prop m]
  (tree-edit (zip/seq-zip prop)
             keyword?
             (fn [_ kw] (get m kw))))

(defn- compile-proposition
  "convert a proposition syntax tree into a function that takes a single map and returns true or false"
  [prop]
  ; first replace all keywords with values from rtuple if they exist, then convert it into a function
  (fn [rtuple] 
    (let [modified-exp (tree-replace-keywords prop rtuple)]
      ;(println "Compiled to: " modified-exp)
      (eval modified-exp))))
      
; TODO: the problem here will be that we want to know what attributes are used in this query when evaluating?
; Should that all happen before this function gets called during the query expression processing?
;(select (= :a 1) #{{:a 1 :b 2} {:a 2 :b 4}})
;
;(defmacro select [prop r]
;  `(set (filter (compile-proposition (quote ~prop)) ~r)))

; removing the macro as this is an internal function and we don't want nested macro processing
;(select '(= :a 1) #{{:a 1 :b 2} {:a 2 :b 4}})
(defn select [prop r]
  (set (filter (compile-proposition prop) r)))

(defn rename [r from to] ; TODO: add ability to rename multiples
  {:pre [(contains? (attrs r) from) ; make sure from is a valid key
         (not (contains? (attrs r) to))]} ; make sure "to" is not a valid key
  (set (map #(dissoc (assoc % to (get % from)) from) r)))

(defn natural-eq? [rtup stup]
  (let [shared (sets/intersection (attrs rtup) (attrs stup))]
    (if (empty? shared) true ; degrade to cartesian product if there are no shared keys
      (every? #(= (get rtup %) (get stup %)) shared))))

(defn natjoin
  "Natural join -values of shared keys are equal"
  [r s]
  ; starting with nested loop join
  ; TODO: implement smarter joins
  ; TODO: implement dumber join - use algebra primitives to re-create
  ; TODO: also don't need to repeatedly determine the shared attributes
  (set (for [x r y s :when (natural-eq? x y)] (merge x y))))

; TODO: since this is a combination of X and σ, this could be expanded in the expression instead of as another function
;(theta-join #{{:a 1 :b 1}} #{{:c 1 :d 2}} '(= :a :c))
;(defmacro theta-join [r s prop]
;  `(select ~prop (cartprod ~r ~s)))
(defn theta-join [r s prop]
  (select prop (cartprod r s)))

; this is a derived operation, don't need primitive function
(defn semijoin [r s]
  (project (natjoin r s) (attrs r)))

; TODO: antijoin
; TODO: division


;
; ---------------------------------------------------------------------------
; Expression tree stuff
; ---------------------------------------------------------------------------
; Basic format [<op-keyword> & exprs]
; expressions are vectors, relations are sets
; TODO: may need to totally re-work to support the optimization stuff

(def expr? vector?)

(defn has-sub-exprs? [exprs]
  (seq (filter expr? exprs)))

; by the time this is called, the sub-expressions have been evaluated
; TODO: rationalize order of arguments
(defmulti eval-expr (fn [op args] op))
(defmethod eval-expr :union [_ [r s]] (rel-union r s))
(defmethod eval-expr :diff [_ [r s]] (rel-diff r s))
(defmethod eval-expr :intersect [_ [r s]] (rel-intersect r s))
(defmethod eval-expr :product [_ [r s]] (cartprod r s))
(defmethod eval-expr :project [_ [r ks]] (project r ks))
(defmethod eval-expr :select [_ [prop r]] (select prop r))
(defmethod eval-expr :rename [_ [r from to]] (rename r from to))


; This allows us to process a syntax tree - the next step will to be processing syntax into this tree
; note manual quoting is still required since we haven't put in a macro yet
; TODO: does this need to be a macro because some of the operations are macros?
(defn query* [q]
   (if (expr? q)
          (let [[op & exprs] q]
            (eval-expr op (map query* exprs)))
          q))

(println (query* [:select '(= :foo 1) [:rename #{{:a 1}{:a 2}{:a 3}} :a :foo]]))
;(println (select '(> :a 2) #{{:a 1}{:a 2}{:a 3}} ))
;(println (theta-join #{{:a 1 :b 1}} #{{:c 1 :d 2}} '(= :a :c)))
