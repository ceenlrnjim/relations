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
