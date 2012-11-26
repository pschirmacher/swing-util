# swing-util

Some Clojure utility functions for working with Swing.

## Usage

"Hello, world!" example. Both the text field and the label are bound to the value of the atom 'name'.

	(show (frame "it works!"
	             (let [name (atom "<name>")]
	               (column :center
	                       (txt 15 name)
	                       (row (label "hi") (label name))))))

A table that shows some data. One column is editable. 'space-cowboys' is updated accordingly.

	(show (frame "table"
	             (let [space-cowboys (atom [{:name "Luke"
	                                         :size "normal"}
	                                        {:name "Yoda"
	                                         :size "small"}
	                                        {:name "Chewbacca"
	                                         :size "big"}])
	                   tm (table-model space-cowboys
	                                   (table-col "Name" :name)
	                                   (table-col "Size" :size :editable true))]
	               (table :model tm))))

## License

Copyright © 2012 pschirmacher

Distributed under the Eclipse Public License, the same as Clojure.
