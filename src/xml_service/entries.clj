(ns xml-service.entries
  (:use     saxon 
     [xml-service utilities paths])
  (:require [xml-service.repos-config :as config])
  (:import  [xml_service AmbiguousMatch NotFound]))

;; Structs for files

(defstruct xml-entry    
            :file 
            :modified 
            :name 
            :repos-path 
            :doc 
            :stylesheets)

(defstruct xsl-entry 
            :file 
            :modified 
            :name 
            :fn)

(defn last-mod [#^java.io.File f] (.lastModified f))
(defn canon-path [#^java.io.File f] (.getCanonicalPath f))

(let 
  [get-pi     
      (compile-xpath "//processing-instruction('spenser-stylesheets')/string()")
   xml-entry-fns
      (struct xml-entry
        identity                                            ;:file
        last-mod                                            ;:modified
        #(basename (canon-path %) ".xml")                   ;:name
        #(reduce-path-by (canon-path %) config/local-repos) ;:repos-path
        ; special cases below: passed doc not file
        identity                                            ;:doc
        #(when-let [raw-names (get-pi %)]                   ;:stylesheets
          (-> raw-names .trim (.split "\\s+") vec)))
   xsl-entry-fns
      (struct xsl-entry
        identity                                            ;:file
        last-mod                                            ;:modified
        #(basename (canon-path %) ".xsl")                   ;:name
        compile-xslt)]                                      ;:fn

  (defn- xml-entry-builder
    "Builds an individual xml entry struct."
    [file]
    (let [doc (compile-xml file)]
      (struct xml-entry
        ((:file xml-entry-fns) file)
        ((:modified xml-entry-fns) file)
        ((:name xml-entry-fns) file)
        ((:repos-path xml-entry-fns) file)
        ((:doc xml-entry-fns) doc)
        ((:stylesheets xml-entry-fns) doc))))

  (defn- xsl-entry-builder
    "Builds an individual xsl entry struct."
    [file]
    (struct xsl-entry
      ((:file xsl-entry-fns) file)
      ((:modified xsl-entry-fns) file)
      ((:name xsl-entry-fns) file)
      ((:fn xsl-entry-fns) file))))

(defn pull-entries-from-fs
  "Returns entries hash-map created with files from repository fs; if 
  entry already exists for file & file hasn't been modified, keeps existing 
  entry & doesn't read file."
  [entries entry-builder files]
  (into {} (map #(let [e (entries %)] 
                  (if (and e (>= (:modified e) (last-mod %)))
                    [(:file e) e]
                    (let [new-entry (entry-builder %)]
                        [(:file new-entry) new-entry])))

              files)))
 
(defn get-xml-entries
  "Convenience function for getting all xml files from repository."
  [xml-entries]
  (pull-entries-from-fs 
    xml-entries 
    xml-entry-builder
    (remove dir? (file-filter-seq config/local-repos dir-and-xml))))

(defn get-xsl-entries
  "Convenience function for getting all stylesheets from repository."
  [xsl-entries]
  (pull-entries-from-fs
    xsl-entries
    xsl-entry-builder
    (remove dir? (file-filter-seq config/local-repos dir-and-xsl))))

;; Entry finders

(defn find-xml
  "Returns XML entry in the entries hash given name parameter."
  [xml-entries name]
  (let [in?           #(.contains % name)
        entries       (vals xml-entries)
        name-matches  (seq (filter in? (map :name entries)))
        repos-matches (when-not name-matches
                        (seq (filter in? (map :repos-path entries))))
        [k result]   
                      (or 
                        (and name-matches  [:name name-matches])
                        (and repos-matches [:repos-path repos-matches])
                        [nil nil])]        
      (if result
          (if (< 1 (count result))
            (throw (AmbiguousMatch. result))
            (some #(and (= (k %) (first result)) %) entries))
          (throw (NotFound. (format "XML file \"%s\" not found." name))))))

(defn find-xsl
  "Returns stylesheet entry, given xml file, xsl-entries, & name. First matches
  name against any stylesheets given in XML file's processing instruction; if
  doesn't find, then matches against all stylesheets in the repository, preferring
  stylesheets closer to the XML file when there's more than one match."
  [xml-entry xsl-entries name]
  (let [in?       #(.contains #^String % name)
        explicits (set (filter in? (:stylesheets xml-entry)))
        filter-fn (if (empty? explicits) in? explicits)
        matched   (filter (comp filter-fn :name) (vals xsl-entries))]
      (cond
        (empty? matched)
           (throw (NotFound. 
                    (format "Stylesheet named \"%s\" not found." name)))
        ; if stylesheet name matched a single XSL entry
        (= 1 (count matched))
          (first matched)
        ; if stylesheet name matched multiple XSL entries
        (< 1 (count matched))
          ; return closest to XML file's directory
          (let [sorted 
                  (sort-by val
                    (into {} (for [sheet matched]
                                [sheet (path-distance 
                                        ((comp dirname canon-path :file) xml-entry)
                                        ((comp dirname canon-path :file) sheet))])))]
            (if (< (val (first sorted)) (val (second sorted)))
              (key (first sorted))
              (let [closest-dist  (val (first sorted))
                    closest-ones  (keys (filter #(= (val %) closest-dist) sorted))]
                (throw (AmbiguousMatch. (map :name closest-ones)))))))))

(let [nses  {:tei   "http://www.tei-c.org/ns/1.0"
             :xhtml "http://www.w3.org/1999/xhtml"}]

  (defn apply-xquery
    "Applies passed XQuery string to XML node, returns result.
    Evaluates query with TEI & XHTML namespaces available."
    [xquery xml]
    ((compile-xquery xquery nses) xml)))

