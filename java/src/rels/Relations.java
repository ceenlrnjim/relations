package rels;

import java.beans.*;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import clojure.lang.PersistentVector;
import clojure.lang.PersistentHashMap;
import clojure.lang.IPersistentMap;

/**
* Library of functions for dealing with Relations in java. Relations are created from lists of maps or java beans.
* Relations created by these functions are always lists of maps.
*
* These functions are based on clojure's persistent data structures. Details of this dependency can be hidden by
* using the fluent interface in the Relation object.
*/
public class Relations {
    /** Note this comparator is only useful for determining equality */
    public static Comparator Eq = new Comparator() {
        public int compare(Object o1, Object o2) {
            return o1.equals(o2) ? 0 : -1;
        }
    };

    // TODO: add detecting of shared properties
    public static PersistentVector nestedLoopJoin(PersistentVector r, PersistentVector s, Object... cond) {
        List<Condition> conditions = parseJoinConditions(cond);
        return nestedLoopJoin(r,s,conditions);
    }

    public static PersistentVector nestedLoopJoin(PersistentVector r, PersistentVector s, Collection<Condition> conditions) {
        PersistentVector results = PersistentVector.EMPTY;
        boolean matches;
        Map r_row, s_row;

        for (Object ri: r) {
            r_row = (Map)ri;
            for (Object si: s) {
                s_row = (Map)si;
                matches = true;
                for (Condition c : conditions) {
                    Object r_val = r_row.get(c.leftAttribute);
                    Object s_val = s_row.get(c.rightAttribute);
                    if (c.comparator.compare(r_val, s_val) != 0) {
                        matches = false;
                        break;
                    }
                }

                if (matches) {
                results = results.cons(mergeMaps(r_row, s_row));
                }
            }
        }

        return results;
    }

    public static PersistentVector hashJoin(PersistentVector r, PersistentVector s, Object... cond) {
        List<Condition> conditions = parseJoinConditions(cond);
        return hashJoin(r,s,conditions);
    }

    // TODO: can only allow equality in this join
    public static PersistentVector hashJoin(PersistentVector r, PersistentVector s, Collection<Condition> conditions) {
        if (conditions.isEmpty()) {
            // cartesian product
            return nestedLoopJoin(r,s,conditions);
        }

        ArrayList rJoinCols = new ArrayList(conditions.size());
        ArrayList sJoinCols = new ArrayList(conditions.size());
        HashMap<List,Map> rHashes = new HashMap<List,Map>();
        PersistentVector results = PersistentVector.EMPTY;

        for (Condition c : conditions) {
            rJoinCols.add(c.leftAttribute);
            sJoinCols.add(c.rightAttribute);
        }

        for (Object r_row : r) {
            // Name could be different and therefore can't be in the hash
            rHashes.put(mapValues((Map)r_row, rJoinCols), (Map)r_row);
        }

        for (Object si : s) {
            Map s_row = (Map)si;
            Map r_row = rHashes.get(mapValues(s_row, sJoinCols));
            if (r_row != null) {
                results = results.cons(mergeMaps(r_row,s_row));
            }
        }

        return results;
    }

    public static List mapValues(Map map, Collection keys) {
        ArrayList result = new ArrayList(keys.size());
        for (Object key : keys) {
            result.add(map.get(key));
        }

        return result;
    }

    public static PersistentVector project(PersistentVector r, Object... keys) {
        return project(r, Arrays.asList(keys));
    }

    public static PersistentVector project(PersistentVector r, final Collection keys) {
        return map(new ItemCallback<Object,Map>() {
            public Map withItem(Object o) {
                Map row = (Map)o;
                IPersistentMap result = PersistentHashMap.EMPTY;
                for (Object k : keys) {
                result = result.assoc(k, row.get(k));
                }
                return (Map)result;
            }
        },r);
    }

    public static PersistentVector select(PersistentVector r, ItemCallback<Map,Boolean> filterFn) {
        return filter(filterFn, r);
    }

    /**
    * returns r with any record where filterFn(row).equals(Boolean.TRUE) modified by modFn(row)
    */
    public static PersistentVector update(PersistentVector r, ItemCallback<Map,Map> modFn, ItemCallback<Map,Boolean> filterFn) {
        PersistentVector result = PersistentVector.EMPTY;
        Iterator i = r.iterator();
        while (i.hasNext()) {
            Map m = (Map)i.next();
            if (filterFn.withItem(m).equals(Boolean.TRUE)) {
                result = result.cons(modFn.withItem(m));
            } else {
            result = result.cons(m);
            }
        }
        return result;
    }

    public static ItemCallback<Map,Boolean> valueFilter(final Object key, final Object value) {
        return new ItemCallback<Map, Boolean> (){
            public Boolean withItem(Map m) {
                return m.get(key) != null && m.get(key).equals(value) ? Boolean.TRUE : Boolean.FALSE;
            }
        };
    }

    public static ItemCallback<Map,Boolean> and(final ItemCallback<Map,Boolean> a, final ItemCallback<Map,Boolean> b) {
        return new ItemCallback<Map,Boolean>() {
                public Boolean withItem(Map m) {
                    return a.withItem(m).booleanValue() && b.withItem(m).booleanValue() ? Boolean.TRUE : Boolean.FALSE;
                }
        };
    }

    public static ItemCallback<Map,Boolean> or(final ItemCallback<Map,Boolean> a, final ItemCallback<Map,Boolean> b) {
        return new ItemCallback<Map,Boolean>() {
            public Boolean withItem(Map m) {
                return a.withItem(m).booleanValue() || b.withItem(m).booleanValue() ? Boolean.TRUE : Boolean.FALSE;
            }
        };
    }

    /**
    * Return a relation that contains only tuples from r where filterFn(row).equals(Boolean.FALSE)
    */
    public static PersistentVector delete(PersistentVector r, ItemCallback<Map,Boolean> filterFn) {
        PersistentVector result = PersistentVector.EMPTY;
        Iterator i = r.iterator();
        while (i.hasNext()) {
            Map m = (Map)i.next();
            if (filterFn.withItem(m).equals(Boolean.FALSE)) {
                result = result.cons(m);
            }
        }

        return result;
    }

    public static Map beanToTuple(Object bean) {
        try {
            BeanInfo bi = Introspector.getBeanInfo(bean.getClass());
            IPersistentMap result = PersistentHashMap.EMPTY;
            for (PropertyDescriptor pd : bi.getPropertyDescriptors()) {
                result = result.assoc(pd.getName(), pd.getReadMethod().invoke(bean));
            }

            return (Map)result;
        } catch (IntrospectionException Ie) {
            throw new RuntimeException(Ie);
        } catch (InvocationTargetException ITe) {
            throw new RuntimeException(ITe);
        } catch (IllegalAccessException IAe) {
            throw new RuntimeException(IAe);
        }
    }

    // TODO: set operations


    // TODO: need to handle case with matching columns names that weren't part of the join
    // currently the s value will overwrite the r - see JS implementation for suffixing
    private static Map mergeMaps(Map r, Map s) {
        IPersistentMap result = PersistentHashMap.EMPTY;

        for (Object key : r.keySet()) {
            result = result.assoc(key, r.get(key));
        }

        for (Object key : s.keySet()) {
            result = result.assoc(key, s.get(key));
        }

        // TODO: what is the appropriate way to get back to a map?
        return (Map)result;
    }

    private static List<Condition> parseJoinConditions(Object[] args) {
        if (args.length % 3 != 0) throw new RuntimeException("args should be multiples of 'col','col',Comparator");
        List result = new ArrayList((int)(args.length / 3));

        for (int i=0;i<args.length;i+=3) {
            result.add(new Condition((String)args[i], (String)args[i+1], (Comparator)args[i+2]));
        }

        return result;
    }

    // Utility functions
    private static void forEach(ItemCallback callback, Iterable iterable) {
        for (Object o : iterable) {
            callback.withItem(o);
        }
    }

    private static PersistentVector map(ItemCallback callback, List iterable) {
        PersistentVector result = PersistentVector.EMPTY;
        for (Object o: iterable) {
            result = result.cons(callback.withItem(o));
        }

        return result;
    }

    private static PersistentVector filter(ItemCallback filter, List iterable) {
        PersistentVector result = PersistentVector.EMPTY;
        for (Object o: iterable) {
            if (filter.withItem(o).equals(Boolean.TRUE)) {
                result = result.cons(o);
            }
        }

        return result;
    }

    // TODO: java bean to map and back converters

    public static Map buildMap(Object... kv) {
        if (kv.length % 2 != 0) throw new RuntimeException("can't build a map from an odd number of arguments");

        return PersistentHashMap.create(kv);
    }

    public static class Condition {
        public final String leftAttribute;
        public final String rightAttribute;
        public final Comparator comparator;

        public Condition(String l, String r, Comparator c) {
            leftAttribute = l;
            rightAttribute = r;
            comparator = c;
        }
    }
}
