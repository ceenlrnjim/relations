module.exports = (function() {
    /*
     * Replace all instances of f in s with r
     */
    var _replaceAll = function(s, f, r) {
        var work = s.replace(f,r);
        if (work === s) return work;
        else return _replaceAll(work, f, r);
    };

    var _not = function(fn) {
        return function() {
            return !fn.apply(null, arguments);
        };
    };

    var _and = function() {
        var fnlist = Array.prototype.slice.apply(arguments);
        return function() {
            var result = true;
            var fnargs = arguments;
            fnlist.forEach(function(fn,i) {
                result = result && fn.apply(null, fnargs);
            });

            return result;
        }
    };

    var _partial = function() {
        var fn = arguments[0];
        var partargs = Array.prototype.slice.apply(arguments).slice(1);
        return function() {
            return fn.apply(null, partargs.concat(Array.prototype.slice.apply(arguments)));
        };
    };

    var _compose = function() {
        var functions = Array.prototype.slice.apply(arguments);
        return function() {
            var result = functions[functions.length-1].apply(null, arguments);
            for (var i=functions.length-2;i>=0;i--) {
                result = functions[i].call(null, result);
            }
            return result;
        };
    };

    /*
     * Given an object and an array of property names, retrieve the value
     * in the object of obj[propNames[0]][propnames[1]][propnames[2]]...
     */
    var _getProperty = function(obj, propNames) {
        if (propNames.length === 1) {
            return obj[propNames[0]];
        } else {
            return _getProperty(obj[propNames[0]], propNames.slice(1));
        }
    };

    /*
     * Sets a nested property on object obj to value
     */
    var _setProperty = function(obj, propNames, value) {
        if (propNames.length === 1) {
            obj[propNames[0]] = value;
        } else {
            if (obj[propNames[0]] === undefined) {
                obj[propNames[0]] = {}; // TODO: arrays
            }
            _setProperty(obj[propNames[0]], propNames.slice(1), value);
        }
    }


    // TODO: I can probably simplify this for tuples
    var _equals = function(m,x) {
      var p;
      for(p in m) {
          if(typeof(x[p])=='undefined') {return false;}
      }

      for(p in m) {
          if (m[p]) {
              switch(typeof(m[p])) {
                  case 'object':
                      if (!m[p].equals(x[p])) { return false; } break;
                  case 'function':
                      if (typeof(x[p])=='undefined' ||
                          (p != 'equals' && m[p].toString() != x[p].toString()))
                          return false;
                      break;
                  default:
                      if (m[p] != x[p]) { return false; }
              }
          } else {
              if (x[p])
                  return false;
          }
      }

      for(p in x) {
          if(typeof(m[p])=='undefined') {return false;}
      }

      return true;
    }



    return { equals: _equals,
             not: _not,
             and: _and,
             partial: _partial,
             compose: _compose,
             replaceAll: _replaceAll,
             getProperty: _getProperty,
             setProperty: _setProperty };

})();
