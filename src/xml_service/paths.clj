(ns xml-service.paths)

;; -- path utilities --

(defn trim-right
  "Removes s2 from right of s1."
  [#^String s1 #^String s2]
  (if (.endsWith s1 s2)
      (.substring s1 0 (- (count s1) (count s2)))
      s1))

(defn trim-left
  "Removes s2 from left of s1."
  [#^String s1 #^String s2]
  (if (.startsWith s1 s2)
      (.substring s1 (count s2))
      s1))

(defn basename
  "Like Unix basename: returns last portion of path."
  [path & suffix]
  (let [result (get (re-find #"(/|[^/]+)/?$" path) 1 "")]
    (if suffix (trim-right result (first suffix)) result)))

(defn dirname
  "Like Unix dirname: returns all but last portion of path."
  [path]
  (if (= path "/")
    path
    (let [path    (trim-right path "/")
          dirname (trim-right path (basename path))]
      (if (> (count dirname) 1)
        (trim-right dirname "/")
        dirname))))

(defn reduce-path-by
  "Trims path from the left with another path. E.g. 
  /home/dir/file.txt reduced by /home is dir/file.txt."
  [path #^String screen]
  (if (= path screen) ""
    (let [screen 
            (if (.endsWith screen "/")
              screen
              (str screen "/"))]
      (trim-left path screen))))

;; Directory distance calculation

(defn dirname-seq
  "Returns a lazy-seq of path and successive calls of dirname on path,
  e.g. /home/bin/text => (/home/bin/text /home/bin /home /)."
  [path]
  (when (not= path "")
    (lazy-seq (cons path (when (not= "/" path)
                            (dirname-seq (dirname path)))))))

(defn num-steps
  "Returns number of steps in a path. E.g. /home/bin => 2;
  /home/bin/text/files.txt => 4. If given multiple paths, adds
  counts of each."
  ([path]
    (count (dirname-seq path)))
  ([path & paths]
    (reduce + (map num-steps (cons path paths)))))

(defn path-distance
  "Calculates distance from one directory (alpha) to another (beta), 
  giving preference to child directories."
  [alpha beta]
  (let [[alpha beta] (map #(trim-right % "/") [alpha beta])
        theyre-equal (= alpha beta)
        a-b-intersect
          (when (not theyre-equal) ; only calculate if needed
            (apply clojure.set/intersection
              (map (comp set dirname-seq) [alpha beta])))]
    (cond
      theyre-equal
        0
      ; if beta is child of alpha
      (a-b-intersect alpha)
        (let [remaining (reduce-path-by beta alpha)]
          ; then weigh distance at half
          (/ (num-steps remaining) 2)) 
      ; if alpha & beta share any structure
      (< 0 (count a-b-intersect))
        ; then reduce common parts & add lengths of dirname-seqs
        (let [common    
                (last (sort-by count a-b-intersect))
              [uniq-a uniq-b] 
                (map #(reduce-path-by % common) [alpha beta])]
            (num-steps uniq-a uniq-b))
      :else
        (num-steps alpha beta))))

