(ns swing-util.core
  (:import [javax.swing JFrame Box BoxLayout JTextField JPanel JSplitPane JLabel JButton JOptionPane JTable
            JScrollPane SwingUtilities JTabbedPane JTextArea ButtonGroup JRadioButton JCheckBox ListSelectionModel
            AbstractButton JToggleButton JComboBox AbstractListModel ComboBoxModel]
           [javax.swing.table AbstractTableModel]
           [javax.swing.event ListSelectionListener DocumentListener ChangeListener]
           [javax.swing.text JTextComponent]
           [java.awt BorderLayout Component GridLayout FlowLayout Dimension]
           [java.awt.event ActionListener ItemListener MouseAdapter]))

(defmacro later
  [& body]
  `(SwingUtilities/invokeLater (fn [] ~@body)))

(defmacro now
  [& body]
  `(SwingUtilities/invokeAndWait (fn [] ~@body)))

(defn row [& components]
  (let [row (JPanel.)]
    (.setLayout row (FlowLayout.))
    (doseq [c components]
      (.add row c))
    row))

(defn right-row [& components]
  (let [row (doto (Box. BoxLayout/LINE_AXIS)
                   (.add (Box/createHorizontalGlue)))]
    (doseq [c components]
      (.add row c))
    row))

(defn left-row [& components]
  (let [row (Box. BoxLayout/LINE_AXIS)]
    (doseq [c components]
      (.add row c))
    (.add row (Box/createHorizontalGlue))
    row))

(defn column [alignment & components]
  (let [alignment-map {:center Component/CENTER_ALIGNMENT
                       :left Component/LEFT_ALIGNMENT
                       :right Component/RIGHT_ALIGNMENT}
        column (Box. BoxLayout/PAGE_AXIS)]
    (doseq [c components]
      (.setAlignmentX c (alignment alignment-map))
      (.add column c))
    column))

(defn grid [row-count col-count & components]
  (let [grid (JPanel. (GridLayout. row-count col-count))]
    (doseq [c components]
      (.add grid c))
    grid))

(defn canvas [width height state-holder draw-fn]
  (let [canvas (proxy [JPanel] []
                 (paintComponent [graphics]
                   (proxy-super paintComponent graphics)
                   (draw-fn graphics @state-holder))
                 (getPreferredSize []
                   (Dimension. width height)))]
    (add-watch state-holder :repaint
               (fn [k r o n]
                 (.repaint canvas)))
    canvas))

(defn on-mouse-clicked [component handler-fn]
  (let [handler (proxy [MouseAdapter] []
                  (mouseClicked [event]
                    (handler-fn event)))]
    (doto component
      (.addMouseListener handler))))

(defn adjust-height
  [& components]
  (let [height #(.getHeight (.getPreferredSize %))
        width #(.getWidth (.getPreferredSize %))
        target-height (apply max (map height components))
        adjust (fn [c h]
                 (doto c
                   (.setPreferredSize (Dimension. (width c) h))
                   (.setMinimumSize (Dimension. (width c) h))
                   (.setMaximumSize (Dimension. (width c) h))))]
    (map #(adjust % target-height) components)))

(defn form
  [& components]
  (let [pairs (map
                #(apply adjust-height %)
                (partition 2 components))
        left-side-components (map first pairs)
        right-side-components (map second pairs)]
    (row
      (apply column :left left-side-components)
      (apply column :right right-side-components))))

(defn tabs
  [& titles-and-components]
  (let [pairs (partition 2 titles-and-components)
        pane (JTabbedPane.)]
    (doseq [[title component] pairs]
      (.addTab pane title component))
    pane))

(defn vertical-split
  [top bottom]
  (doto (JSplitPane.)
    (.setOrientation JSplitPane/VERTICAL_SPLIT)
    (.setTopComponent top)
    (.setBottomComponent bottom)))

(defn horizontal-split
  [lft rght]
  (doto (JSplitPane.)
    (.setOrientation JSplitPane/HORIZONTAL_SPLIT)
    (.setLeftComponent lft)
    (.setRightComponent rght)))

(defn scrollable
  [component]
  (JScrollPane. component))

(defn string-from
  [textcomponent]
  (if-let [t (.getText textcomponent)]
    (.trim t)
    ""))

(defn int-from
  [textcomponent]
  (try
    (Integer. (string-from textcomponent))
    (catch Exception e
      nil)))

(defmacro keeping-caret-position
  [textcomponent & body]
  `(let [cp# (.getCaretPosition ~textcomponent)
         result# (do ~@body)]
     (try
       (.setCaretPosition ~textcomponent cp#)
       (catch Exception e#
         (.setCaretPosition ~textcomponent
           (count (.getText ~textcomponent)))))
     result#))

(defmacro without-document-listener
  [textcomponent listener & body]
  `(do
     (.. ~textcomponent getDocument (removeDocumentListener ~listener))
     (let [result# (do ~@body)]
       (.. ~textcomponent getDocument (addDocumentListener ~listener))
       result#)))

(defmulti data-bind (fn [target data] [(type target) (type data)]))

(defmethod data-bind [JTextComponent String]
  [target data]
  (.setText target data)
  target)

(defmethod data-bind [JTextComponent clojure.lang.Atom]
  [target data]
  (let [sync-atom #(reset! data (.getText target))
        listener (proxy [DocumentListener] []
                   (changedUpdate [e] (sync-atom))
                   (insertUpdate [e] (sync-atom))
                   (removeUpdate [e] (sync-atom)))
        watch-fn (fn [k r o n] (when (not= n (.getText target))
                                 (later
                                   (without-document-listener target listener
                                     (keeping-caret-position target
                                                             (.setText target n))))))]
    (.setText target @data)
    (.. target getDocument (addDocumentListener listener))
    (add-watch data target watch-fn)
    target))

(defmethod data-bind [JToggleButton Boolean]
  [target data]
  (doto target
    (.setSelected data)))

(defmethod data-bind [JToggleButton clojure.lang.Atom]
  [target data]
  (let [sync-atom #(reset! data (.isSelected target))
        listener (proxy [ItemListener] []
                   (itemStateChanged [_] (sync-atom)))
        watch-fn (fn [k r o n]
                   (.removeItemListener target listener)
                   (.setSelected target n)
                   (.addItemListener target listener))]
    (.setSelected target @data)
    (.addItemListener target listener)
    (add-watch data target watch-fn)
    target))

(defmethod data-bind [JLabel String]
  [target data]
  (.setText target data))

(defmethod data-bind [JLabel clojure.lang.Atom]
  [target data]
  (.setText target (str @data))
  (add-watch data target (fn [k r o n]
                           (.setText target (str n)))))

(defmulti action-bind (fn [target action] [(type target) (type action)]))

(defmethod action-bind [AbstractButton clojure.lang.IFn]
  [target action]
  (.addActionListener target
                      (proxy [ActionListener] []
                        (actionPerformed [event] (action event))))
  target)

(defmethod action-bind [JTable clojure.lang.PersistentVector]
  [target [action-type action-fn]]
  (condp = action-type
    :row-selected (.. target getSelectionModel (addListSelectionListener
                                                 (proxy [ListSelectionListener] []
                                                   (valueChanged [e] (when-not (.getValueIsAdjusting e) (action-fn)))))))
  target)

(defmethod action-bind [JTabbedPane clojure.lang.IFn]
  [target action]
  (.addChangeListener target
                      (reify ChangeListener
                        (stateChanged [this event]
                          (action event))))
  target)

(defn txt
  ([data]
    (doto (JTextField.)
      (data-bind data)))
  ([cols data]
    (doto (JTextField.)
      (.setColumns cols)
      (data-bind data))))

(defn txt-area
  [rows cols data & {:keys [editable] :or {editable true}}]
  (doto (JTextArea. rows cols)
    (.setEditable editable)
    (data-bind data)))

(defn button
  [text action]
  (doto (JButton. text)
    (action-bind action)))

(def button-group 
  (memoize (fn [_] (ButtonGroup.))))

(defn radio-button
  ([text selected action]
    (radio-button text selected nil action))
  ([text selected button-group-key action]
    (let [button (doto (JRadioButton. text)
                   (data-bind selected)
                   (action-bind action))]
      (when button-group-key
        (.add (button-group button-group-key) button))
      button)))

(defn check-box
  [text selected action]
  (doto (JCheckBox. text)
    (data-bind selected)
    (action-bind action)))

(defn label
  [data]
  (doto (JLabel.)
    (data-bind data)))

(defn combo-box
  ([items-holder]
    (combo-box items-holder identity))
  ([items-holder key-fn]
    (let [selected (atom nil)
          model (proxy [AbstractListModel ComboBoxModel] []
                  (getElementAt [idx] (key-fn (get @items-holder idx)))
                  (getSize [] (count @items-holder))
                  (setSelectedItem [item] (reset! selected item))
                  (getSelectedItem [] @selected))]
      (add-watch items-holder model (fn [k r o n]
                                      (let [from-idx 0
                                            to-idx (count n)]
                                        (later
                                          (.setSelectedItem model nil)
                                          (.fireContentsChanged model model from-idx to-idx)))))
      (JComboBox. model))))

(defn selected-index
  [combo-box]
  (.getSelectedIndex combo-box))

(defn table-col
  [col-name key-fn & {:keys [editable update-fn] :or {editable false
                                                      update-fn (fn [row new-cell-value]
                                                                  (assoc row key-fn new-cell-value))}}]
  {:name col-name
   :key-fn key-fn
   :editable editable
   :update-fn update-fn})

(defn table-model
  [rows-holder & columns]
  (let [column-names (vec (map :name columns))
        update-rows (fn [idx updater]
                      (swap! rows-holder (fn [old-rows]
                                           (let [old-cell (get (vec old-rows) idx)
                                                 new-cell (updater old-cell)]
                                             (assoc old-rows idx new-cell)))))
        atm (proxy [AbstractTableModel] []
              (getColumnName [col] (get column-names col))
              (getRowCount [] (count @rows-holder))
              (getColumnCount [] (count columns))
              (getValueAt [row col]
                (let [the-row (get @rows-holder row)
                      the-col (get (vec columns) col)]
                  ((:key-fn the-col) the-row)))
              (isCellEditable [row col]
                (let [the-col (get (vec columns) col)]
                  (:editable the-col)))
              (getColumnClass [col]
                (if-let [value (.getValueAt this 0 col)]
                  (class value)
                  Object))
              (setValueAt [value row col]
                (let [update-fn (:update-fn (get (vec columns) col))]
                  (update-rows row #(update-fn % value)))))]
    (add-watch rows-holder atm (fn [k r old-state new-state]
                                 (later (.fireTableDataChanged atm))))
    atm))

(defn table
  [& {:keys [model viewport-size fills-viewport-height] :or {fills-viewport-height true}}]
  (let [table (JTable. model)]
    (when viewport-size (.setPreferredScrollableViewportSize table
                          (Dimension. (first viewport-size) (second viewport-size))))
    (.. table getSelectionModel (setSelectionMode javax.swing.ListSelectionModel/SINGLE_SELECTION))
    (doto table
      (.setFillsViewportHeight fills-viewport-height))))

(defn selected-row
  [table]
  (if (>= (.getSelectedRow table) 0)
    (.getSelectedRow table)
    nil))

(defn enable
  [component]
  (.setEnabled component true)
  component)

(defn disable
  [component]
  (.setEnabled component false)
  component)

(defn enabled?
  [component]
  (.isEnabled component))

(defn frame
  [title container & {:keys [resizable] :or {resizable false}}]
  (doto (JFrame. title)
    (.setResizable resizable)
    (.setContentPane container)))

(defn show
  [frame]
  (doto frame
    .pack
    (.setVisible true)))

(defn error-dialog
  [frame msg]
  (JOptionPane/showMessageDialog frame msg "Oops!" JOptionPane/ERROR_MESSAGE))

(defmacro with-exception-handling
  [frame & body]
  `(try
     ~@body
     (catch Exception e#
       (later
         (error-dialog ~frame
                       (.getMessage e#))))))

