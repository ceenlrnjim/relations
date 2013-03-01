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
