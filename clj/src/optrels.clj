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
      (eval modified-exp))))
      

(defmacro proposition [p]
  `(compile-proposition p))


; TODO: support arbitrary functions?
(defn selection [r prop]
