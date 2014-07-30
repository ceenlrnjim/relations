(ns optrels
  (:require [clojure.set :as sets])
  (:require [clojure.zip :as zip]))

; Note - don't support getting attributes for empty relations
(defn attrs [r]
  {:pre [(seq r) ; not empty
         (map? (first r))]} ; containg maps
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
  {:pre [(or (empty? r) (empty? (sets/difference ks (attrs r))))]} ; all ks are valid columns in r or r is empty (can't determine attrs)
  (if (empty? r) #{}
    (set (map #(select-keys % ks) r))))

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
;(restrict '(= :a 1) #{{:a 1 :b 2} {:a 2 :b 4}})
(defn restrict [prop r]
  (set (filter (compile-proposition prop) r)))

(defn rename [r from to] ; TODO: add ability to rename multiples
  {:pre [(contains? (attrs r) from) ; make sure from is a valid key
         (not (contains? (attrs r) to))]} ; make sure "to" is not a valid key
  (set (map #(dissoc (assoc % to (get % from)) from) r)))

(defn- natural-eq? [rtup stup]
  (let [shared (sets/intersection (set (keys rtup)) (set (keys stup)))]
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
;  `(restrict ~prop (cartprod ~r ~s)))
; note - need to manually quote prop
(defn theta-join [r s prop]
  (restrict prop (cartprod r s)))

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
; TODO: may want access to the attribute keywords in the syntax tree for optimization
; expressions are vectors, relations are sets
; TODO: may need to totally re-work to support the optimization stuff

(def expr? vector?)

; by the time this is called, the sub-expressions have been evaluated
; TODO: rationalize order of arguments
(defmulti eval-expr (fn [op args] op))
(defmethod eval-expr :union [_ [r s]] (rel-union r s))
(defmethod eval-expr :diff [_ [r s]] (rel-diff r s))
(defmethod eval-expr :intersect [_ [r s]] (rel-intersect r s))
(defmethod eval-expr :product [_ [r s]] (cartprod r s))
(defmethod eval-expr :project [_ [r ks]] (project r ks))
(defmethod eval-expr :restrict [_ [prop r]] (restrict prop r))
(defmethod eval-expr :rename [_ [r from to]] (rename r from to))
(defmethod eval-expr :natjoin [_ [r s]] (natjoin r s))
(defmethod eval-expr :join [_ [r s prop]] (theta-join r s prop))
(defmethod eval-expr :semijoin [_ [r s]] (semijoin r s))


; This allows us to process a syntax tree - the next step will to be processing syntax into this tree
; note manual quoting is still required since we haven't put in a macro yet
(defn query* 
  "evaluate an expression tree for a query"
  [q]
  (if (expr? q)
      (let [[op & exprs] q]
        (eval-expr op (map query* exprs)))
      q))

; -----------------------------------------------------------------------------
; Actual expression generating stuff
; The Query language
; Shooting for a programmatic (non-macro) version and a human (macro) interface
; This is all pretty dodgy and should make use of an actual parser
; TODO: could I use actual lists instead of vectors?

; [x y z] = [:product [:product x y] z]
(defn build-joins [rs]
  (if (seq (rest rs)) ; more than one value
    (reduce (fn [a v] [:natjoin a v]) rs) ; default to natural join (which defaults to cross product if no shared attributes)
    (first rs)))


; start with simple cartesian product version

; select :a as :d in macro
; (expand-select [[:a :d] :b :c] ... in function
(defn expand-renames
  "operates on project expression"
  [expr] 
  (let [[op rel ks] expr
        simplified-ks (set (map #(if (sequential? %) (first %) %) ks))]
    (loop [renames (filter sequential? ks)
           result [op rel simplified-ks]]
      (if (seq renames)
        (let [[from to] (first renames)]
          (recur (rest renames) [:rename result from to]))
        result))))

; Moves logic on building the expression tree out of the macro
(defn expand-select
  ([ats rels] 
    (expand-select ats rels nil))
  ; TODO: in order to optimize the expression tree, I need visibility into the condition
  ; so, as per the wikipedia article I'm going to limit the functions you can use - how to remove this restriction later?
  ; This is an academic exercise, so I'm not too worried
  ([ats rels prop] 
    (let [j (build-joins rels)
          project-target (if (nil? prop) j [:restrict prop j])]
    (if (and (empty? (rest ats)) (= (first ats) '*)) 
      project-target
      (expand-renames
        [:project 
          project-target
          (set ats)])))))

(defn convert-rename-syntax [ats]
  (if (seq ats)
    (let [[n as alias & more] ats]
      (if (= as 'as)
        (cons [n alias] (convert-rename-syntax more))
        (cons n (convert-rename-syntax (rest ats)))))
    []))

; This version doesn't require selected attributes and relations to be in vectors - is this better?
; probably need real parsing here
; select :a :b :c from x y where (= :a :b)
(defmacro select [& details]
  (let [[ats# rem1#] (split-with #(not= % 'from) details)
        renamed-ats# (convert-rename-syntax ats#)
        [rels# rem2#] (split-with #(not= % 'where) (rest rem1#)) ; drop the 'from and take until "where" or the end
        prop# (second rem2#)]
      (expand-select renamed-ats# rels# (if (nil? prop#) nil `(quote ~prop#)))))
  

; start by generating the expression tree
; (query (select []...)
;        union
;        (select []...)
;        minus
;        (select []...))
(defn expand-query
  ([sel] sel)
  ; Note: converting op to keyword in the macro - should it be here?
  ([sel1 op sel2] [op sel1 sel2])
  ([sel1 op sel2 & pairs] 
    {:pre [(even? (count pairs))]}
    (loop [remaining-pairs pairs
           expr [op sel1 sel2]]
      (if (seq remaining-pairs)
          (let [[op rel] (take 2 remaining-pairs)]
            (recur (drop 2 remaining-pairs) [op expr rel]))
          expr))))

(defmacro query [& body]
  (let [kw-converted (map #(if (not (list? %)) (keyword %) %) body)]
    (list query* (concat (list expand-query) kw-converted))))

;------------------------------------------------------------------------------
;Expression Optimization
;------------------------------------------------------------------------------
; For now we're going to assume that all keywords are attribute references in the tuple - TODO: lift this restriction by more specific identifier?
; First part: break a selection expression that combines multiple attributes into multiple selections
;  1) from first relation
;  2) from second relation
;  3) from combined relation for shared attributes
; this will be used to "push" down selection before executing the join
; [:restrict (= :a 1) [:natjoin #{{:a 0} {:a 1} {:a 2}} #{{:b 1}}]] => [:natjoin [:restrict (= :a 1) #{{:a 1}{:a 0}{:a 2}}] #{{:b 1}}]
; [:restrict (and (= :b 1) (= :a 1)) [:natjoin #{{:a 0} {:a 1} {:a 2}} #{{:b 1}}]] => [:natjoin [:restrict (= :a 1) #{{:a 1}{:a 0}{:a 2}}] [:restrict (= :b 1) #{{:b 1}}]]

  ;"determines what the attribute set is for each node in the tree, without evaluating the results"
  ; for now putting the set of resulting attributes as the last item in the vector where it should be ignored by the evaluator
(defn- expr-attrs [r]
  (cond (expr? r) (last r)
        (set? r) (attrs r)
        :else #{}))

(defmulti analyze-expr (fn [op args] op))
(defmethod analyze-expr :union [op [r s]]
  (let [ats (if (empty? r) (expr-attrs s) (expr-attrs r))]
    [op r s ats]))
(defmethod analyze-expr :diff [op [r s]]
  (let [ats (if (empty? r) (expr-attrs s) (expr-attrs r))]
    [op r s ats]))
(defmethod analyze-expr :intersect [op [r s]]
  (let [ats (if (empty? r) (expr-attrs s) (expr-attrs r))]
    [op r s ats]))
(defmethod analyze-expr :cartprod [op [r s]]
  ; TODO: haven't yet checked for disjoint headers
  (let [ats (sets/union (expr-attrs r) (expr-attrs s))]
    [op r s ats]))
(defmethod analyze-expr :project [op [r ks]]
  [op r ks ks])
(defmethod analyze-expr :restrict [op [prop r]]
  [op prop r (expr-attrs r)])
(defmethod analyze-expr :rename [op [r from to]]
  [op r from to (conj (disj (expr-attrs r) from) to)])
(defmethod analyze-expr :natjoin [op [r s]]
  (let [ats (sets/union (expr-attrs r) (expr-attrs s))]
    [op r s ats]))
;
; This is where it gets tricky requiring data to have the metadata (attribute names) mixed in with the data (the keys in the maps)
; TODO: same deal as query* - abstract
(defn analyze [expr]
  (if 
    (expr? expr)
    (let [[op & exprs] expr]
      (analyze-expr op (map analyze exprs))) ; need to analyze depth-first
    expr))

; TODO: starting with only checking one level up - can I make this more generic to push down multiple levels?
(defn join-then-restrict? 
  "Returns true if this expression is a selection/restriction of a join result"
  [expr]
  (let [[op arg1 arg2 & args] expr]
    (and (= op :restrict)
         (expr? arg2)
         (= (first arg2) :natjoin))))

; TODO: need to know the attributes that will result from each node as it stands - need to determine what the attributes of a relation are, 
; but that relation might itself be another expression
(defn push-down-restrict [expr]
  expr)

; TODO: starting with only checking if all attributes are in one relation of the join - next step is to break the proposition into multiple restrictions
(defn keywords 
  "Retrieves a set of the keywords used in the proposition"
  [prop]
  (when (seq prop)
    (let [lists (filter list? prop)
          ks (filter keyword? prop)]
      (concat ks (mapcat keywords lists)))))

(defn has-sub-expr? [expr]
  (some expr? expr))

(defn optimize-expr [expr]
  (if (has-sub-expr? expr)
    (cond (join-then-restrict? expr) (push-down-restrict expr)
          :else expr)
    expr))


(def r #{{:a 1 :b 10 :c 100}{:a 2 :b 2 :c 200}{:a 3 :b 30 :c 300}})
(def s #{{:d 1 :name "foo"}{:d 2 :name "bar"}})
(def q #{{:d 1 :age 27}{:d 2 :age 506}})

