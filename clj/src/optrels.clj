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


(defn- annotate-with-attrs
  [expr ats]
  (with-meta expr {:attributes ats}))
  ;(conj expr ats))

(defn- expression-analysis-results
  "extracts the analyzed set of expression results"
  [expr]
  (:attributes (meta expr)))
  ;(last expr))
  

;"determines what the attribute set is for each node in the tree, without evaluating the results"
(defn- expr-attrs [r]
  "Returns the set of attributes that the specified set or expression will result in"
  (cond (expr? r) (expression-analysis-results r)
        (set? r) (attrs r)
        :else #{}))

(defn keywords 
  "Retrieves a set of the keywords used in the proposition"
  [prop]
  (when (seq prop)
    (let [lists (filter list? prop)
          ks (filter keyword? prop)]
      (set (concat ks (mapcat keywords lists))))))


(defmulti analyze-expr (fn [op args] op))
(defmethod analyze-expr :union [op [r s]]
  (let [ats (if (empty? r) (expr-attrs s) (expr-attrs r))]
    (annotate-with-attrs [op r s] ats)))
(defmethod analyze-expr :diff [op [r s]]
  (let [ats (if (empty? r) (expr-attrs s) (expr-attrs r))]
    (annotate-with-attrs [op r s] ats)))
(defmethod analyze-expr :intersect [op [r s]]
  (let [ats (if (empty? r) (expr-attrs s) (expr-attrs r))]
    (annotate-with-attrs [op r s] ats)))
(defmethod analyze-expr :cartprod [op [r s]]
  ; TODO: haven't yet checked for disjoint headers
  (let [ats (sets/union (expr-attrs r) (expr-attrs s))]
    (annotate-with-attrs [op r s] ats)))
(defmethod analyze-expr :project [op [r ks]]
  (annotate-with-attrs [op r ks] ks))
(defmethod analyze-expr :restrict [op [prop r]]
  (annotate-with-attrs [op prop r] (expr-attrs r)))
(defmethod analyze-expr :rename [op [r from to]]
  (annotate-with-attrs [op r from to] (conj (disj (expr-attrs r) from) to)))
(defmethod analyze-expr :natjoin [op [r s]]
  (let [ats (sets/union (expr-attrs r) (expr-attrs s))]
    (annotate-with-attrs [op r s] ats)))
;
; This is where it gets tricky requiring data to have the metadata (attribute names) mixed in with the data (the keys in the maps)
; TODO: same deal as query* - abstract
(defn analyze [expr]
  (if 
    (expr? expr)
    (let [[op & exprs] expr]
      (analyze-expr op (map analyze exprs))) ; need to analyze depth-first
    expr))

(defn and-proposition? [[op arg1 & args]]
  (and (= op :restrict)
       (= (first arg1) 'and)))

; TODO: missing recursion for nested ands?  Does that make sense?  What if ands/or are mixed in sub-expressions 
; -> need to maintain logical equality
(defn decompose-and-proposition [[op prop rel]]
  "Breaks a :restrict node with a compound proposition joined with and into multiple restrict nodes"
  (loop [conditions (drop 2 prop)
         expr [op (second prop) rel]] ; create a new restriction node with the first condition
    (if (seq conditions)
      (recur (rest conditions) [op (first conditions) expr])
      (analyze expr))))
    

(defn push-down-restrict [expr]
  ;(println "---------------------------------------------")
  ;(println expr)
  (let [[restrict-op prop rel] expr
        ks (keywords prop)]
    ; descend until we find either a relation, or multiple expressions that use keywords in ks
    (analyze 
      (if (not (expr? rel))
        expr ; not an expression so it can't be pushed down
        (let [terms-using-keywords (map-indexed  
                                    ; this is a vector of [true/false index relation-term] 
                                    ; indicating if the item at that position uses keywords in the proposition
                                    (fn [ix v] 
                                      [(and 
                                          ; find just sets and relations (ignore propositions and operation keyword)
                                          (or (expr? v) (set? v)) 
                                          ; that contain at least one attribute used in the proposition
                                          (not (empty? (sets/intersection ks (expr-attrs v)))))
                                        ix v])
                                      rel) ; strip off the set added during analysis
              kw-uses (filter first terms-using-keywords)]
          (if (> (count kw-uses) 1)
            ; properties in the proposition come from multiple sub-expressions, so it can't be pushed down
            [restrict-op prop rel] 
            ; find the one index where terms-using-keywords is true
            ; return "rel" with that index replaced with push-down-restrict [restrict-op prop (the item)]
            ; TODO: count = 0?
            (let [[t ix sub-expr] (first kw-uses)] ; we know there is only one match here
              (assoc rel ix (push-down-restrict [restrict-op prop sub-expr])))))))))


(defn has-sub-expr? [expr]
  (some expr? expr))

(defn optimize-restrict [init-expr]
  (let [[init-op arg1 & args] init-expr]
    (loop [expr init-expr
           op init-op
           prop arg1]
      (let [opt-exp (push-down-restrict (if (and-proposition? expr) (decompose-and-proposition expr) expr))
            [opt-op opt-arg1 & args] opt-exp]
        ; loop until we don't get any change or no more restrict clauses
        (if (or (not= opt-op :restrict)
                (= prop opt-arg1))
            opt-exp
            (recur opt-exp opt-op opt-arg1))))))
   

; TODO: multiple push downs - starts getting into comparing plans and costs? want equality lower than inequality conditions?
(defn optimize-expr [init-expr]
  (analyze
    (let [opt-subs (if (has-sub-expr? init-expr) (mapv #(if (expr? %) (optimize-expr %) %) init-expr) init-expr)
          [init-op arg1 & args] opt-subs]
      (if (= :restrict init-op) (optimize-restrict opt-subs) opt-subs))))
  
(comment
(defn optimize-expr [expr]
  (if (has-sub-expr? expr)
      (let [opt-exp (mapv #(if (expr? %) (optimize-expr %) %) expr) ; this expression with sub expressions optimized
            ]
           ; result 
            (cond 
                    (= (first opt-exp) :restrict) (push-down-restrict (if (and-proposition? opt-exp) (decompose-and-proposition opt-exp) opt-exp))
                        :else opt-exp))
        ; TODO: need to repeatedly optimize until there is no change
    expr))
)

; (query (select []...)
;        union
;        (select []...)
;        minus
;        (select []...))

(defmacro query [& body]
  "converts set operation words into keywords, generates an expression tree, and evaluates it"
  (let [kw-converted (map #(if (not (list? %)) (keyword %) %) body)]
    (list query* (optimize-expr (analyze (concat (list expand-query) kw-converted))))))



(def r #{{:a 1 :b 10 :c 100}{:a 2 :b 2 :c 200}{:a 3 :b 30 :c 300}})
(def s #{{:d 1 :name "foo"}{:d 2 :name "bar"}})
(def q #{{:d 1 :age 27}{:d 2 :age 506}})


(comment
(println "Building data")
(defn make-data-2-row [i] {:child-id i :parent-id (+ i 900) :child-name (str "child_" i)})
(def data2 (into #{} (map make-data-2-row (range 100))))
(defn make-data-1-row [i] {:id i :name (str "row" i)})
(def data1 (into #{} (map make-data-1-row (range 1000))))

(def theq (select * from data1 data2 where (and (= :id :parent-id) (= :child-id 50) (> :parent-id 900))))
(println "Executing un-optimized...")
(println (time (query* theq))) ; approx 55 seconds
(println "Executing optimized...")
(println (time (query* (optimize-expr (analyze theq))))) ; approx 665 msecs
)
