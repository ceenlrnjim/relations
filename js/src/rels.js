module.exports = (function() {
    var tupret = require("./tupret.js");
    var utils = require("./utils.js");
    var not = utils.not;
    var partial = utils.partial;
    // TODO: starting with object tuples - add array cases later (need column names)
    
    var _analyzeJoinColumns = function(r,s,c) {
        // TODO: support for array tuples
        var r_rep = r[0];
        var s_rep = s[0];
        var prop;
        var distinctProps = [];
        var sharedProps = [];
        var result = { allProperties: distinctProps };

        for (prop in r_rep) {
            if (r_rep.hasOwnProperty(prop)) {
                distinctProps.push(prop);
            }
        }

        for (prop in s_rep) {
            if (s_rep.hasOwnProperty(prop)) {
                if (!r_rep.hasOwnProperty(prop)) {
                    distinctProps.push(prop);
                } else {
                    sharedProps.push(prop);
                }
            }
        }

        // Since we don't have an explicit list of join attributes we'll use
        // whatever attributes are shared between the relations
        if (c === undefined) {
            result.joinCols = [];
            sharedProps.forEach(function(p) {
                result.joinCols.push([p,p]);
            });
        } else {
            result.joinCols = c;
        }

        return result;
    };
    
    /** returns a list of property names that are in a but not b */
    var _uniqueProperties = function(a,b) {
        var p;
        var result = [];
        for (p in a) { 
            if (b[p] === undefined) {
                result.push(p);
            }
        }

        return result;
    };

    var _mergeAllProperties = function(a,b) {
        var p;
        var result = {};
        for (p in a) { result[p] = a[p]; }
        for (p in b) { result[p] = b[p]; }
        return result;
    };

    var _mergeTuples = function(r,s,properties) {
        // TODO: support for array tuples
        var result = {};
        properties.forEach(function(prop) {
            // if we have non-joined columns that match, can't have two properties with the same name
            // so currently using the _l/_r prefixes to signify which side they came from
            // TODO: need to consider the impact of hasOwnProperty versus prototype inherited properties
            // - I probably want some ability to use tuples that are objects with prototype inherited properties
            if (r.hasOwnProperty(prop)) {
                if (s.hasOwnProperty(prop)) {
                    result[prop + "_l"] = r[prop];
                } else {
                    result[prop] = r[prop];
                }
            }
            if (s.hasOwnProperty(prop)) {
                if (r.hasOwnProperty(prop)) {
                    result[prop + "_r"] = s[prop];
                } else {
                    result[prop] = s[prop];
                }
            }
        });

        return result;
    };

    var _containsRow = function(r, row) {
        return _select(r, function(i) { return utils.equals(row, i); }).length > 0;
    };

    /*
    * Returns the natural join of two relations (list of tuple - or simple js objects in this case).
    * Natural join is set of all combinations of contents of r and s that are equal in their
    * shared attributes.
    *
    * c is an array of two or three element arrays where each pair contains a property name in r and a property name in s
    *  and an optional third element that is the function to be used to compare them (defaults to equality)
    * to be compared.  
    *
    * This implementation assumes that these are relations - that all js objects have the same properties.
    */
    var _join = function(r,s,c) {
        // TODO: starting with nested loop join - look at improving efficiency with another algorithm (sort-merge join, hash-join, etc.)
        var result = [];
        var attributes = _analyzeJoinColumns(r,s,c);
        r.forEach(function(ri) {
            s.forEach(function(sj) {
                var matches = true;

                // TODO: probably want a regular loop so we can return early once we know it doesn't match
               attributes.joinCols.forEach(function(spec) {
                    var r_prop = spec[0], s_prop = spec[1];
                    var compFn = spec.length === 3 ? spec[2] : function(a,b) { return a === b; }; // can optimize by checking for undefined instead of using equality function
                    if (!compFn(ri[r_prop],sj[s_prop])) {
                        matches = false;
                    }
                });

                if (matches) {
                    result.push(_mergeTuples(ri,sj,attributes.allProperties));
                }
            });
        });

        return result;
    };

    /**
     * Takes an array of javascript objects (r) whose property (p) is a collection
     * and returns an array of relations where
     * - 1 entry per combination of r and p
     * - each entry has the properties of p and the properties of the r that contains it.
     * This is used to convert a One-to-many object into a tuple/relation
     */
    var _flatten = function(r, p) {
        var result = [];
        r.forEach(function(ri) {
            if (ri[p] !== undefined) {
                ri[p].forEach(function(ripj) {
                    // need to filter out property p
                    var merged = _mergeTuples(ri, ripj, _analyzeJoinColumns([ri],[ripj],[]).allProperties); 
                    delete merged[p];
                    result.push(merged);
                });
            }
        });

        return result;
    };

    /**
     * returns an array of tuples that contains all tuples in array r restricted to only
     * attributes in attrArray
     */
    var _project = function(r, attrArray) {
        return r.map(function(t) {
            return attrArray.reduce(function(row, prop) { row[prop] = t[prop]; return row; }, {});
        });
    };

    /**
     * Returns all tuples t in r where fn(t) === true
     */
    var _select = function(r, fn) {
        return r.filter(fn);
    };

    /**
     * Returns a new relation (array of tuples) where the properties in 'from' are renamed to the properties in 'to'
     */
    var _rename = function(r,from,to) {
        var fromArray = from instanceof Array ? from : [from];
        var toArray = to instanceof Array ? to : [to];
        var results = [];
        var prop;
        var index;

        return r.map(function(row) {
            var newrow = {};
            for (prop in row) {
                index = from.indexOf(prop);
                if (index !== -1) {
                    newrow[to[index]] = row[prop];
                } else {
                    newrow[prop] = row[prop];
                }
            }
            return newrow;
        });
    };

    /**
     * modify all tuples t in array r where filterFn(t) === true to be modFn(t)
     */
    var _update = function(r, modFn, filterFn) {
        for (var i=0;i<r.length;i++) {
            if (filterFn(r[i])) {
                r[i] = modFn(r[i]);
            }
        }
    };

    /**
     * Remove all tuples t in r where filterFn(t) === true
     */
    var _del = function(r, filterFn) {
        var matches = [];
        for (var i=0; i<r.length;i++) {
            if (filterFn(r[i])) {
                matches.push(i);
            }
        }

        for (var i=matches.length-1;i>=0;i--) {
            r.splice(matches[i],1);
        }
    };

    // TODO: set operations below may not appropriately handle duplicate rows
    // (might get duplicate matches where they should be distinct)

    /**
     * return all the tuples that are in both r and t
     */
    var _intersect = function(r, s) {
        return r.filter(partial(_containsRow, s));
    };

    /**
     * Return only the tuples in r that are not in s
     */
    var _difference = function(r,s) {
        return r.filter(not(partial(_containsRow,s)));
    };

    /**
     * Return all tuples that are in r or s
     */
    var _union = function(r,s) {
        return r.concat(s.filter(not(partial(_containsRow, r))));
    };

    /**
     * TODO: multiple functions and columns
     */
    var _aggregate = function(r, prop, fn, initial) {
        return r.reduce(function(aggr, row) {
                            return fn(aggr, row[prop]);
                        },
                       initial);
    };
    
    /**
     * Adds derived column(s).  All the properties of the object returned by callback fn will
     * be unioned with the properties of that row to form the row in the returned relation
     */
    var _appendDerived = function(r, fn) {
        return r.map(function(row) {
            var newvals = fn(row);
            return _mergeAllProperties(row, newvals);
        });
    };

    var _divide = function(r,s) {
        var uniqueR = _uniqueProperties(r[0], s[0]);
        var restrictedR = _project(r, uniqueR);
        var cartProd = _join(restrictedR, s);
        var possibleButNot = _difference(cartProd, r);
        var restrictedPossibleButNot = _project(possibleButNot, uniqueR);
        return _distinct(_difference(restrictedR, restrictedPossibleButNot));

    };

    var _distinct = function(r) {
        return r.reduce(function(result, row) { 
            if (!_containsRow(result, row)) {
                result.push(row);
            }
            return result;
        }, []);
    };

    /*
     * applies multiple projections to single relation.
     * First argument r is the relation.
     * Remaining arguments are arrays of the properties for each projection.
     * Returns an array of relations corresponding to the specified projections
     */
    var _projectMultiple = function() {
        var r = arguments[0];
        var projections = Array.prototype.slice.apply(arguments).slice(1);
        var results = projections.map(function() { return []; });

        r.forEach(function(srcrow) {
            var tgtrow;
            var proj;
            for (var i=0;i<projections.length;i++) {
                tgtrow = {};
                proj = projections[i];
                proj.forEach(function(prop) {
                    tgtrow[prop] = srcrow[prop];
                });
                results[i].push(tgtrow); 
            }
        });
        return results;
    };

    /*
     * This function is meant to break apart a relation that represents a hierarchical join
     * between parent/child entities where the parent attributes are replicated on each child row.
     * First arg is the relation, remaining arguments are arrays of the columns corresponding
     * to each entity
     */
    var _unjoin = function() {
        return _projectMultiple.apply(null, arguments).map(_distinct);
    };


    //
    // Object with a prototype of the above functions to allow chaining -
    // Relation object functions assume arguments are Relations as well
    //
    var Relation = function(data) {
        this.data = data;
    };
    Relation.prototype.join = function(s,c) {
        return new Relation(_join(this.data,s.data,c));
    };
    Relation.prototype.project = function(attrs) {
        return new Relation(_project(this.data, attrs));
    };
    Relation.prototype.select = function(fn) {
        return new Relation(_select(this.data, fn));
    };
    Relation.prototype.rename = function(from, to) {
        return new Relation(_rename(this.data, from, to));
    };
    Relation.prototype.forEach = function(fn) {
        this.data.forEach(fn);
    };
    Relation.prototype.insert = function(row) {
        // can directly update the array of objects if using functions
        // directly instead of the Relation object - so no stand alone function is required
        this.data.push(row);
        return this;
    };
    Relation.prototype.update = function(modFn, filterFn) {
        _update(this.data, modFn, filterFn);
        return this;
    };
    Relation.prototype.del = function(filterFn) {
        _del(this.data, filterFn);
        return this;
    };
    Relation.prototype.union = function(r) {
        return new Relation(_union(this.data, r.data));
    };
    Relation.prototype.intersect = function(r) {
        return new Relation(_intersect(this.data, r.data));
    };
    Relation.prototype.difference = function(r) {
        return new Relation(_difference(this.data, r.data));
    };
    Relation.prototype.aggregate = function(prop, fn, initial) {
        return _aggregate(this.data, prop, fn, initial);
    };
    Relation.prototype.map = function(fn) {
        return new Relation(this.data.map(fn));
    };
    Relation.prototype.appendDerived = function(fn) {
        return new Relation(_appendDerived(this.data,fn));
    };
    Relation.prototype.divideBy = function(r) {
        return new Relation(_divide(this.data, r.data));
    };
    Relation.prototype.distinct = function() {
        return new Relation(_distinct(this.data));
    };
    Relation.prototype.length = function() {
        return this.data.length;
    };
    Relation.prototype.projectMultiple = function() {
        var projections = Array.prototype.slice.apply(arguments);
        var p = _projectMultiple.apply(null, [this.data].concat(projections));
        console.log(p);
        return p.map(function(r) { return new Relation(r); });
    };
    Relation.prototype.unjoin = function() {
        var projections = Array.prototype.slice.apply(arguments);
        var p = _unjoin.apply(null, [this.data].concat(projections));
        return p.map(function(r) { return new Relation(r); });
    };
    // Effectively combines select, project, and rename
    Relation.prototype.match = function(pattern) {
        var matches = [];
        tupret.forEach(pattern, this.data, function(row) {
            matches.push(row);
        });

        return new Relation(matches);
    };




    // TODO:
    // aggregations / group by / having
    // outer joins,
    // indices in the Relation object?
    // divide
    // do I want some kind of non-mutable insert/update
    // projectMultiple
    // unjoin 
    // unflatten

    // utility functions for use as arguments
    var _propEq = function(name, value) {
        return function(o) { return o[name] === value;};
    }

    return {join: _join,
            project: _project,
            select: _select,
            flatten: _flatten,
            rename: _rename,
            update: _update,
            del: _del,
            intersect: _intersect,
            union: _union,
            difference: _difference,
            aggregate: _aggregate,
            propEq: _propEq,
            appendDerived: _appendDerived,
            divide: _divide,
            distinct: _distinct,
            projectMultiple: _projectMultiple,
            unjoin: _unjoin,
            Relation: Relation};
})();
