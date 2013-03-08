(ns tupret
  (:require [clojure.core.reducers :as r]))

(defn fresh?
  [pval]
  (and (keyword? pval) (.startsWith (name pval) "?")))

(defn fresh-name
  [pval]
  (keyword (.substring (name pval) 1)))

(defn key-seq
  [tuple-collection]
  (cond (map? tuple-collection) (keys tuple-collection)
        (vector? tuple-collection) (range 0 (count tuple-collection))
        :else nil)) ; TODO: probably need a throws

(defn binding-set
  [pattern tuple]
  ; divide the keys in pattern into those that are fresh (fk) and those that specify values (vk)
  (let [{vk false fk true} (group-by #(fresh? (get pattern %)) (key-seq pattern))]
    ; when all the keys whose values are not fresh have the same values in the pattern and the tuple
    (when (every? #(= (get pattern %) (get tuple %)) vk)
      ; assoc all the keys whose values are fresh in the pattern to the equivalent value in this tuple
      (reduce #(assoc %1 (fresh-name (get pattern %2)) (get tuple %2)) {} fk))))

(defn pattern-matches
  [tuples pattern]
  (r/foldcat
    (r/filter (comp not nil?)
      (r/map (partial binding-set pattern) tuples))))
