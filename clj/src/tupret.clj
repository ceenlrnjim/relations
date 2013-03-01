(ns tupret)

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

; TODO add support for list/vector tuples as well - no keys, just sequnce of matching values
(defn binding-set
  "patter and tuple are both maps"
  [pattern tuple]
  (loop [result {}
         keyseq (key-seq pattern)]
    (if (empty? keyseq) result
      (let [k (first keyseq)
            pval (get pattern k)
            tval (get tuple k)]
        (if (fresh? pval) 
            (recur (assoc result (fresh-name pval) tval) (next keyseq))
            (if (= pval tval) 
                (recur result (next keyseq))
                (recur nil nil)))))))


(defn patternMatches
  [pattern tuples]
  (loop [ tupleseq tuples
         result [] ]
    (if 
      (empty? tupleseq) result
      (let [bs (binding-set pattern (first tupleseq))]
        (if (nil? bs) 
          (recur (next tupleseq) result)
          (recur (next tupleseq) (conj result bs)))))))
