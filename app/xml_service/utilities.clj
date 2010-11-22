(ns xml-service.utilities
  (:use [clojure.contrib.seq-utils :only (frequencies)]
        [clojure.contrib.str-utils :only (str-join)]))

(defn set-count
  "Reduces collection to a hash whose keys are the unique items
  in the collection & whose values are the number of times the
  item appears. Hash is sorted by number, most frequent first."
  [coll]
  (sort-by val                    ; grab val of hash item
      (comp - compare))           ; descending order
      (frequencies coll))

(defn combo-if-coll
  "Concatenates vector items, if arg is vector; otherwise 
  returns argument. Takes optional separator argument, which 
  defaults to space."
  ([arg] (combo-if-coll " " arg))
  ([sep arg] (if (coll? arg), (str-join sep arg), arg)))

(defmacro transform-keyval
  "Transforms key-value pairs according to passed form. Passed 
  form will have the vars 'k' & 'v' available to it, & will be 
  evaulated for each key-value pair. Results of evaluation 
  should be a vector of new [key val]."
  [hashmap form]
  `(into {} (map (fn ~'[[k,v]] ~form) ~hashmap)))

(defn uuid
  "Returns randomly generated UUID (as string)."
  []
  (.toString (java.util.UUID/randomUUID)))

(defn as-file 
  {:tag java.io.File}
  [f]
  (if (string? f) (java.io.File. f) f))

(defn as-abs-path
  {:tag String}
  [p]
  (if (instance? java.io.File p) (.getAbsolutePath #^java.io.File p) p))

(defn delete-rec 
  ([f] (delete-rec (as-file f) true))
  ([#^java.io.File f no-check] 
    (when (.isDirectory f) 
      (doseq [c (.listFiles f)] (delete-rec c true))) 
    (.delete f)))

(defn throw-fmt
  [fmt-str & args]
  (throw (Exception. (apply format fmt-str args))))

;; Directory walker stuff

(defn dir? [#^java.io.File f] (.isDirectory f))

(defn file-filter-seq 
  "Like file-seq, but takes a FileFilter."
  [dir #^java.io.FileFilter file-filter]
  (tree-seq
    (fn [#^java.io.File f] (dir? f))
    (fn [#^java.io.File f] (seq (.listFiles f file-filter)))
    (java.io.File. dir)))

(def dir-and-xml
  (proxy [java.io.FileFilter] []
    (accept [#^java.io.File file] 
      (let [name (.getName file)]
        (and (not (.startsWith name "."))
              (or (dir? file) (.endsWith name ".xml")))))))

(def dir-and-xsl
  (proxy [java.io.FileFilter] []
    (accept [#^java.io.File file] 
      (let [name (.getName file)]
        (and (not (.startsWith name "."))
             (or (dir? file) (.endsWith name ".xsl")))))))


