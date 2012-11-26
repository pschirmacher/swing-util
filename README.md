# swing-util

Some Clojure utility functions for working with swing.

## Usage

	(show (frame "it works!"
	                     (let [name (atom "<name>")]
	                       (column :center
	                               (txt 15 name)
	                               (row (label "hi") (label name))))))

## License

Copyright © 2012 pschirmacher

Distributed under the Eclipse Public License, the same as Clojure.
