rpg (Relational PlayGround)
===========================

Playing with the idea of using relations in applications

Clojure version of the javascript implementation

Import the library and set up a local alias (I like short code)

    user=> (use 'rels)
    nil
    user=> (defn printrows [r] (doseq [i r] (println i)))
    #'user/printrows


Now I can define some relations which are just sequences of maps

    (def people [
             { :first "John", :last "Doe", :department "Sales" },
             { :first "Jane", :last "Doe", :department "Corporate" },
             { :first "Bill", :last "Smith", :department "IT" }])
    #'user/people
    (def locations [
             { :department "Sales", :street "123 abc ave.", :state "NY", :zip 12345 },
             { :department "IT", :street "124 abc ave.", :state "NY", :zip 12345 },
             { :department "Corporate", :street "42 A street.", :state "NH", :zip 54321 }])
    #'user/locations

Now I can use my relational library functions to do things like join my datasets together, either automatically using shared properties, or with an explicit list of join conditions and properties

    user=> (printrows (join people locations))
    {:last Doe, :state NY, :first John, :street 123 abc ave., :department Sales, :zip 12345}
    {:last Doe, :state NH, :first Jane, :street 42 A street., :department Corporate, :zip 54321}
    {:last Smith, :state NY, :first Bill, :street 124 abc ave., :department IT, :zip 12345}
    nil

Or I can apply projection to limit the number of columns returned

    user=> (printrows (project (join people locations) [:first :last :state]))
    {:state NY, :last Doe, :first John}
    {:state NH, :last Doe, :first Jane}
    {:state NY, :last Smith, :first Bill}
    nil

I can do selection to filter the entries

    user=> (printrows (select people #(= (:last %) "Doe")))
    {:last Doe, :first John, :department Sales}
    {:last Doe, :first Jane, :department Corporate}
    nil

There is also a kind of pattern based tuple matching

    user=> (use 'tupret)
    nil
    user=> (printrows (pattern-matches {:first :?first :last "Doe" :department :?dept} people))
    {:dept Sales, :first John}
    {:dept Corporate, :first Jane}
    nil

