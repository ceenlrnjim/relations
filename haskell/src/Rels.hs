import qualified Data.Map as Map

type RelTuple = Map.Map String String
type Relation = [RelTuple]

project :: Relation -> [String] -> Relation
project [] _ = []
project (t:ts) keys = (fst (Map.partitionWithKey (\k _ -> k `elem` keys) t)) : (project ts keys)

select :: Relation -> (RelTuple -> Bool) -> Relation
select r f = filter f r

selectWhereKey :: Relation -> String -> String -> Relation
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

main = let r = [Map.fromList [("a","1"),("b","11"),("c","111")], 
                Map.fromList [("a","2"),("b","22"),("c","222")], 
                Map.fromList [("a","3"),("b","33"),("c","333")]]
           s = [Map.fromList [("a","1"),("d","1111"),("e","11111")],
                Map.fromList [("a","2"),("d","2222"),("e","22222")]]
    in 
        do
        putStrLn "Projection"
        putStrLn $ show $ project r ["a","c"]
        putStrLn "Selection"
        putStrLn $ show $ selectWhereKey r "a" "2"
        putStrLn "Simple Join"
        putStrLn $ show $ join r s ["a"]
        putStrLn "Cartesian Product"
        putStrLn $ show $ join r s []
        putStrLn "Rename"
        putStrLn $ show $ rename r "c" "newC"
