-- TODO: check out  {-# LANGUAGE ExistentialQuantification #-} support for heterogenous lists to define n-tuples
import qualified Data.Map as Map
import qualified Data.List as L
import Data.Monoid

--
-- Generic map versions
--
type RelNum = Num
data RelVal = Str String
              | RelNum
              deriving (Show, Eq, Ord)

type RelTuple = Map.Map String RelVal
type Relation = [RelTuple]



project :: Relation -> [String] -> Relation
project [] _ = []
project (t:ts) keys = (fst (Map.partitionWithKey (\k _ -> k `elem` keys) t)) : (project ts keys)

select :: Relation -> (RelTuple -> Bool) -> Relation
select r f = filter f r

selectWhereKey :: Relation -> String -> RelVal -> Relation
selectWhereKey r k v = select r (\m -> (m Map.! k) == v)

-- only supporting a list of columns and equality matching for now, column names must match - adding rename to support
join :: Relation -> Relation -> [String] -> Relation
join rs ss cols = [Map.union r s | r <- rs, s <- ss, colsmatch r s cols] 

colsmatch :: RelTuple -> RelTuple -> [String] -> Bool
colsmatch r s cols = (project [r] cols) == (project [s] cols)

replaceKey :: RelTuple -> String -> String -> RelTuple
replaceKey r from to = Map.delete from (Map.insert to (r Map.! from) r)

rename :: Relation -> String -> String -> Relation
rename r from to = map (\rt -> replaceKey rt from to ) r


--
-- Record versions
--
recJoin :: [a] -> [b] -> (a -> b -> Bool) -> [(a,b)]
recJoin rs ss f = recJoinMerge rs ss f (\x y -> (x,y))

-- allow user to specify how to merge the two records
recJoinMerge :: [a] -> [b] -> (a -> b -> Bool) -> (a -> b -> c) -> [c]
recJoinMerge rs ss f m = [m r s|r <- rs, s <- ss, f r s]

-- TODO: support multiple join columns
recJoinByField :: (Eq c) => [a] -> [b] -> (a->c) -> (b->c) -> [(a,b)]
recJoinByField rs ss fr fs = recJoinByFields rs ss [(fr,fs)]

recJoinByFields :: (Eq c) => [a] -> [b] -> [((a->c), (b->c))] -> [(a,b)]
recJoinByFields rs ss colpairs = [(r,s)| r <- rs, s <- ss, all (\(fr,fs) -> (fr r) == (fs s)) colpairs]

-- TODO: make work for both fst and snd
groupByFst :: (Eq a) => [(a,b)] -> [(a,[b])]
groupByFst r = let grouped = L.groupBy (\(xa, _) (ya, _) -> xa == ya) r
               in   map (\((xa,xb):xs) -> foldl (\(_, bs) (a,b) -> (a, b:bs)) (xa,[xb]) xs) grouped
               

data Person = Person { personName :: String,
                       personId :: Int,
                       psnDeptId :: Int } deriving (Show)

data Dept = Dept { deptName :: String,
                   deptId :: Int } deriving (Show)

main = let r = [Map.fromList [("a", Str "1"),("b", Str "11"),("c", Str "111")], 
                Map.fromList [("a", Str "2"),("b", Str "22"),("c", Str "222")], 
                Map.fromList [("a", Str "3"),("b", Str "33"),("c", Str "333")]]
           s = [Map.fromList [("a", Str "1"),("d", Str "1111"),("e", Str "11111")],
                Map.fromList [("a", Str "2"),("d", Str "2222"),("e", Str "22222")]]
           people = [(Person "Jim" 1 1),(Person "Jane" 2 1),(Person "John" 3 2),(Person "Joan" 4 3)]
           depts = [(Dept "IT" 1),(Dept "Sales" 2), (Dept "Wet Ops" 3), (Dept "Security" 4)]
    in 
        do
        putStrLn "Projection"
        putStrLn $ show $ project r ["a","c"]
        putStrLn "Selection"
        putStrLn $ show $ selectWhereKey r "a" $ Str "2"
        putStrLn "Simple Join"
        putStrLn $ show $ join r s ["a"]
        putStrLn "Cartesian Product"
        putStrLn $ show $ join r s []
        putStrLn "Rename"
        putStrLn $ show $ rename r "c" "newC"
        putStrLn "Join By Field for Records ----------------------------------------------"
        putStrLn $ show $ recJoinByField people depts psnDeptId deptId
