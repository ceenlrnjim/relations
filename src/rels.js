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

    var _mergeTuples = function(r,s,properties) {
        // TODO: support for array tuples
        var result = {};
        properties.forEach(function(prop) {
            // if we have non-joined columns that match, can't have two properties with the same name
            // so currently using the _l/_r prefixes to signify which side they came from
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
                    // TODO: need to filter out property p
                    result.push(_mergeTuples(ri, ripj, _analyzeJoinColumns([ri],[ripj],[]).allProperties));
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
            Relation: Relation};
})();

