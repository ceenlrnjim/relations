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
