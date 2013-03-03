exports.testProject = function(test) {
    var rels = require("../src/rels.js");
    var data = [ {a: 1, b:2, c:3},
                 {a: 4, b:5, c:6} ];

    var p = rels.project(data, ["a","c"]);
    test.ok(p[0].a !== undefined);
    test.ok(p[1].a !== undefined);
    test.ok(p[0].c !== undefined);
    test.ok(p[1].c !== undefined);
    test.ok(p[0].b === undefined);
    test.ok(p[1].b === undefined);
    test.done();
};

exports.testSelect = function(test) {
    var rels = require("../src/rels.js");
    var propEq = rels.propEq;
    var data = new rels.Relation([{a:1, b:'a'},
                                  {a:2, b:'b'},
                                  {a:3, b:'c'},
                                  {a:4, b:'d'},
                                  {a:5, b:'e'}]);

   var result = data.select(propEq("a",3));
   var id3 = result.data[0];
   test.ok(result instanceof rels.Relation);
   test.ok(result.data.length === 1);
    
   test.ok(id3.a === 3);
   test.ok(id3.b === 'c');
   test.done();
};

exports.testDerive = function(test) {
    var rels = require("../src/rels.js");
    var data = new rels.Relation([{a:1, b:'a'},
                                  {a:2, b:'b'},
                                  {a:3, b:'c'},
                                  {a:4, b:'d'},
                                  {a:5, b:'e'}]);
    var updated = data.appendDerived(function(row) { return { asq: row.a * row.a }; });
    test.ok(updated.select(rels.propEq("a", 1)).data[0].asq === 1);
    test.ok(updated.select(rels.propEq("a", 2)).data[0].asq === 4);
    test.ok(updated.select(rels.propEq("a", 3)).data[0].asq === 9);
    test.ok(updated.select(rels.propEq("a", 4)).data[0].asq === 16);
    test.ok(updated.select(rels.propEq("a", 5)).data[0].asq === 25);
    test.done();
};

exports.testMap = function(test) {
    var rels = require("../src/rels.js");
    var data = new rels.Relation([{a:1, b:'a'},
                                  {a:2, b:'b'},
                                  {a:3, b:'c'},
                                  {a:4, b:'d'},
                                  {a:5, b:'e'}]);
    var updated = data.map(function(row) { return { a: row.a * row.a, b: row.b }; });
    test.ok(updated.select(rels.propEq("a", 1)).data[0].b === 'a');
    test.ok(updated.select(rels.propEq("a", 4)).data[0].b === 'b');
    test.ok(updated.select(rels.propEq("a", 9)).data[0].b === 'c');
    test.ok(updated.select(rels.propEq("a", 16)).data[0].b === 'd');
    test.ok(updated.select(rels.propEq("a", 25)).data[0].b === 'e');
    test.done();

};

exports.testDistinct = function(test) {
    var rels = require("../src/rels.js");
    var and = require("../src/utils.js").and;
    var propEq = rels.propEq;
    var data = new rels.Relation([{a:1, b:'a'},
                                  {a:2, b:'b'},
                                  {a:2, b:'c'},
                                  {a:2, b:'b'},
                                  {a:1, b:'a'}]);
    
    var result = data.distinct();
    test.ok(result.length() === 3);
    test.ok(result.select(rels.propEq("a",1)).length() == 1);
    test.ok(result.select(and(propEq("a",2), propEq("b","b"))).length() === 1);
    test.ok(result.select(and(propEq("a",2), propEq("b","c"))).length() === 1);
    test.done();

}

exports.testDivide = function(test) {
    // straight outta wikipedia
    var rels = require("../src/rels.js");
    var completed = new rels.Relation([ {s: "Fred", t: "db1"},
                                        {s: "Fred", t: "db2"},
                                        {s: "Fred", t: "comp1"},
                                        {s: "Eugene", t: "db1"},
                                        {s: "Eugene", t: "compiler1"},
                                        {s: "Sarah", t: "db1"},
                                        {s: "Sarah", t: "db2"}]);
    var dbProject = new rels.Relation([{ t:"db1"}, {t:"db2"}]);
    var result = completed.divideBy(dbProject).data;
    test.ok(result.length === 2, "length was " + result.length);
    test.ok(result[0].s === "Fred");
    test.ok(result[1].s === "Sarah");
    test.done();
};
exports.testAnd = function(test) {
    var and = require("../src/utils.js").and;
    var rangetest = and(function(a) { return a > 5; }, function(a) { return a < 10; });
    test.ok(rangetest(7));
    test.ok(!rangetest(1));
    test.done();
};

exports.testProjectMultiple = function(test) {
    var rels = require("../src/rels.js");
    var data = [{ a: 1, b: 11, c: 111, d:1111},
                { a: 2, b: 22, c: 222, d:2222},
                { a: 3, b: 33, c: 333, d:3333},
                { a: 4, b: 44, c: 444, d:4444},
                { a: 5, b: 55, c: 555, d:5555},
                { a: 6, b: 66, c: 666, d:6666}];

    // TODO: need to test the Relation method version as well
    var result = new rels.Relation(data).projectMultiple(["a","c"], ["d","b"]);
    test.ok(result.length === 2, "incorrect number of projections");
    test.ok(result[0].data.length === 6, "incorrect number of rows (0)");
    test.ok(result[1].data.length === 6, "incorrect number of rows (1)");
    test.ok(result[0].data[0].a !== undefined, "a missing from 0");
    test.ok(result[0].data[0].c !== undefined, "c missing from 0");
    test.ok(result[0].data[0].b === undefined, "unexpected b");
    test.ok(result[0].data[0].d === undefined, "unexpected d");
    test.ok(result[1].data[0].a === undefined, "unexpected a");
    test.ok(result[1].data[0].c === undefined, "unexpected c");
    test.ok(result[1].data[0].b !== undefined, "missing b");
    test.ok(result[1].data[0].d !== undefined, "missing d");
    test.done();
};

exports.testUnjoin = function(test) {
    var data = [
    {a: 1, b:1, c: 1, d:11, e: 111},
    {a: 1, b:1, c: 1, d:11, e: 112},
    {a: 1, b:1, c: 1, d:12, e: 121},
    {a: 1, b:1, c: 1, d:12, e: 122},
    {a: 2, b:2, c: 2, d:21, e: 211},
    {a: 2, b:2, c: 2, d:21, e: 212},
    {a: 2, b:2, c: 2, d:22, e: 221},
    {a: 2, b:2, c: 2, d:22, e: 222}];

    var rels = require("../src/rels.js");

    var r = new rels.Relation(data);
    var results = r.unjoin(["a","b","c"], ["a","d"], ["a","d","e"]);
   // var results = rels.unjoin(data,["a","b","c"], ["a","d"], ["a","d","e"]);
    test.ok(results.length === 3);
    test.ok(results[0].data.length === 2);
    test.ok(results[1].data.length === 4);
    test.ok(results[2].data.length === 8);
    console.log(results[0].join(results[1]).rename("a_l","a").project(["a","b","c","d"]).join(results[2]).rename(["a_l","d_l"],["a","d"]).project(["a","b","c","d","e"]));
    test.done();
};
