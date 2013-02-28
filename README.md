rpg (Relational PlayGround)
===========================

Playing with the idea of using relations in applications instead of objects

Implementing some relational tools for javascript to combine relational and 
functional models.

Functions are exposed as raw functions that take an array of tuples or
as members of the Relation class to allow chaining.

tuples are javascript objects


Import the library and set up a local alias (I like short code)
        ```
        > var rels = require("./src/rels.js");
        undefined
        > var Relation = rels.Relation;
        undefined
        ```

Now I can define some relations which are just an array of tuples.
Tuples are just javascript objects.  There are some limits to what it will handle in terms of nesting.
    ```
    > var people = [
    ...     { first: "John", last: "Doe", department: "Sales" },
    ...     { first: "Jane", last: "Doe", department: "Corporate" },
    ...     { first: "Bill", last: "Smith", department: "IT" }];
    undefined
    > var locations = [
    ...     { department: "Sales", street: "123 abc ave.", state: "NY", zip: 12345 },
    ...     { department: "IT", street: "124 abc ave.", state: "NY", zip: 12345 },
    ...     { department: "Corporate", street: "42 A street.", state: "NH", zip: 54321 }];
    undefined
    ```

Now I can use my relational library functions to do things like join my datasets together, either automatically using shared properties, or with an explicit list of join conditions and properties
    ```
    > rels.join(people, locations);
    [ { first: 'John',
        last: 'Doe',
        department_l: 'Sales',
        department_r: 'Sales',
        street: '123 abc ave.',
        state: 'NY',
        zip: 12345 },
      { first: 'Jane',
        last: 'Doe',
        department_l: 'Corporate',
        department_r: 'Corporate',
        street: '42 A street.',
        state: 'NH',
        zip: 54321 },
      { first: 'Bill',
        last: 'Smith',
        department_l: 'IT',
        department_r: 'IT',
        street: '124 abc ave.',
        state: 'NY',
        zip: 12345 } ]
    ```

Or I can apply projection to limit the number of columns returned
    ```
    > rels.project(rels.join(people, locations), ["first","last","state"]);
    [ { first: 'John',
        last: 'Doe',
        state: 'NY' },
      { first: 'Jane',
        last: 'Doe',
        state: 'NH' },
      { first: 'Bill',
        last: 'Smith',
        state: 'NY' } ]
    ```


If you like a more fluent object style interface you can wrap your data in a Relation object.  Most relation functions return instances of Relation.
    ```
    > var peopleRel = new Relation(people);
    undefined
    > var locationRel = new Relation(locations);
    undefined
    > peopleRel.join(locationRel).project(["first","last","state"]);
    { data: 
       [ { first: 'John',
           last: 'Doe',
           state: 'NY' },
         { first: 'Jane',
           last: 'Doe',
           state: 'NH' },
         { first: 'Bill',
           last: 'Smith',
           state: 'NY' } ] }
    ```

I can do selection to filter the entries
    ```
    > peopleRel.select(function(person) { return person.last === "Doe"; });
    { data: 
       [ { first: 'John',
           last: 'Doe',
           department: 'Sales' },
         { first: 'Jane',
           last: 'Doe',
           department: 'Corporate' } ] }
    ```

Or if you like even more brevity build condition functions that return functions
    ```
    > var propEq = rels.propEq;
    undefined
    > peopleRel.select(propEq("last", "Doe"));
    { data: 
       [ { first: 'John',
           last: 'Doe',
           department: 'Sales' },
         { first: 'Jane',
           last: 'Doe',
           department: 'Corporate' } ] }
    ```

There is also a kind of pattern based tuple matching
    ```
    > peopleRel.match("{first: ?first, last: 'Doe', department: ?dept}");
    { data: 
       [ { first: 'John', dept: 'Sales' },
         { first: 'Jane', dept: 'Corporate' } ] }
    ```
