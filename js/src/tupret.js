// Pattern based tuple retrieval functions - tupret = tuple retrieval
//
//
// TODO: do I want to add the ability to provide column names in array cases and use an object pattern with array data?

module.exports = (function() {
    var utils = require("./utils.js");
    var topScope = this;
    var patternVarRegex = /\?[a-zA-Z]*/g;

    //
    //Monkey patch with impunity
    //
    if (!String.prototype.replaceAll) {
        String.prototype.replaceAll = function(f,r) {
            return utils.replaceAll(this, f, r);
        };
    }

    /*
     * Class to represent a pattern variable that needs to be bound
     * to any matches
     */
    var FreshVar = function(name) {
        this.name = name;
        this.comparatorFunction;
    }

    /*
     * Class that represents an arbitraty condition specified in the pattern
     */
    var Condition = function(varname, fnspec, placeholder) {
        this.fnspec = fnspec;
        this.varname = varname;
        this.placeholder = placeholder || "~";
    }
    Condition.prototype = {
        satisfies: function(v) {
            var evalString = this.fnspec.replaceAll(this.placeholder, v);
            //console.log("Condition.satisfies: " + evalString);
            return (0,eval)(evalString);
        }
    };


    /*
     * Handle a user specified condition string in the pattern
     */
    var _parseCondition = function(condstring) {
        var result = {};
        var workingString = condstring.slice(2,-2); //remove "earmuff" identifiers

        var valuePlaceholders = workingString.match(patternVarRegex);
        valuePlaceholders.forEach(function(v) {
            workingString = workingString.replace(v, "~");
            result.varname = v.slice(1); // multiple will overwrite, assuming all have to be the same
        });

        result.fnspec = workingString;
        return result;
    }

    /*
     * Takes a pattern string as provided by the user and
     * convert it into a javascript object with Condition and FreshVar properties
     * where specified by special strings in the pattern
     */
    var _evalPattern = function(pattern) {
        // find variables identified as ?someName
        var freshVars = pattern.match(patternVarRegex);
        var resultString = pattern;
        var explicitConditions;
        var parsedCondition;

        // replace any explicitly specified comparisons
        explicitConditions = resultString.match(/#{.*}#/g);
        if (explicitConditions != null) {
            explicitConditions.forEach(function(c) {
                parsedCondition = _parseCondition(c);
                resultString = resultString.replace(c, "new Condition('"+ parsedCondition.varname + "','" + parsedCondition.fnspec + "')");
            });
        }

        // replace each variable witha FreshVar instance
        if (freshVars != null) {
            freshVars.forEach(function(v) {
                resultString = resultString.replace(v, "new FreshVar('" + v.substring(1) + "')");
            });
        }

        // eval the resulting javascript object (tuple)
        //console.log("Eval: " + resultString);
        return eval("(" + resultString + ")");
    };


    /**
     * Returns an array containing all the properties and sub properties of the specified object.
     * Sub properties are returned as a dot delimited string of property names
     */
    var _properties = function(compiledPattern, prefix) {
        var properties = [];
        var pf = prefix || "";
        for (p in compiledPattern) {
            if (compiledPattern.hasOwnProperty(p)) {
                if (typeof compiledPattern[p] === 'object' &&
                    //!(compiledPattern[p] instanceof Array) &&
                    !(compiledPattern[p] instanceof FreshVar) &&
                    !(compiledPattern[p] instanceof Condition)) {
                    properties = properties.concat(_properties(compiledPattern[p], p + "."));
                //} else if (compiledPattern[p] instanceof Array) {
                //    properties.push(pf + p);
                } else {
                    properties.push(pf + p);
                }
            }
        }

        return properties;
    };


    /*
     * Returns false if the specified tuple doesn't match the specified pattern.
     * Otherwise returns an object whose properties are the pattern variables and whose values
     * are the corresponding property values from the specified tuple
     */
    var _bindingSet = function(pattern, tuple) {
        var result = {};
        var compiledPattern = _evalPattern(pattern);
        var p;
        var propNames;
        var properties = _properties(compiledPattern);
        var tupleValue;

        // Not using forEach since I want to be able to early return
        for (var i=0;i<properties.length;i++) {
            propNames = properties[i].split(".");
            p = utils.getProperty(compiledPattern, propNames);

            //console.log("binding " + propNames + " with value " + p);
            if (p instanceof FreshVar) {
                result[p.name] = utils.getProperty(tuple, propNames);
            } else if (p instanceof Condition) {
                tupleValue = utils.getProperty(tuple,propNames);
                if (p.satisfies(tupleValue)) {
                    result[p.varname] = tupleValue;
                } else {
                    return false;
                }
            } else if (!(p instanceof Condition) && utils.getProperty(compiledPattern, propNames) !== utils.getProperty(tuple, propNames)) {
                //console.log("default to equals - not matching");
                return false;
            } else {
                // bound but equal, we can continue checking the match
            }
        };

        return result;
    };

    //
    // "Public" functions -----------------------------------------------------
    //

    var _forEach = function(pattern, tuples, matchFn) {

        tuples.forEach(function(t) {
            var match = _bindingSet(pattern, t);
            if (match) {
                matchFn(match);
            }
        });
    };

    // TODO: can I implement the bound-unbound map version?  Do I have an appropriate
    // map implementation given the general purpose nature of the library?

    // TODO: need to make sure you're code is modifying tuples
    /*
    var _whileMatches = function(pattern, tuples, matchFn) {
        var containsMatch = true;
        var match;

        while (containsMatch) {
            containsMatch = false;
            for (var i=0;i<tuples.length;i++) {
                match = _bindingSet(pattern, tuples[i]);
                if (match) {
                    containsMatch = true;
                    matchFn(match);
                    break;
                }
            }
        }
    };

    var _ifHasMatch = function(pattern, tuples, matchFn, elseFn) {
        var match;
        for (var i=0;i<tuples.length;i++) {
           match = _bindingSet(pattern, tuples[i]);
           if (match) {
               matchFn(match);
               return;
           }
        }

        if (elseFn !== undefined) {
            elseFn();
        }
    };
    */

    return {forEach: _forEach};
}());

