(ns xml-service.http-utilities
  (:use compojure
        [clojure.contrib.json :only (json-str)])
  (:import (xml_service NotFound AmbiguousMatch)))

(defn templ
  "HTML template."
  ([pieces-map] ; may contain :title, :css, :js, :body
    [{"Content-Type" "application/xhtml+xml;charset=UTF-8"}
     (html (doctype :xhtml-strict)
       (xhtml-tag "en"
         [:head
           [:title (:title pieces-map "")]
           (apply include-css (:css pieces-map ["index.css"]))
           (apply include-js (:js pieces-map))]
         [:body
           (:body pieces-map)]))])
  ([title & body]
    (templ {:title title :body body})))

;; Return doc handlers

(defn handle-ambiguous
  "Returns error for ambiguous selection of entries."
  [coll]
  [404
    (templ "Ambiguous"
      [:p "Ambiguous match; found: "
        (unordered-list coll)])])

(defn handle-error
  "Returns error message."
  [msg]
  [500 ; NOT SURE IF RIGHT STATUS CODE
    (templ "Error"
      [:p (format "Error: %s" msg)])])

(defn get-cause
  "Returns first cause of Exception."
  [#^Exception e]
  (if-let [cause (.getCause e)]
    (recur cause)
    e))

(defn instance?*
  "Like instance?, but returns instance argument instead of true."
  [cls i]
  (when (instance? cls i) i))

(defmacro catch-all
  "Tries body with any AmbiguousMatch, NotFound errors caught
  & handled appropriately."
  [& body]
  `(try 
     ~@body
     (catch Exception e#
       (condp instance?* (get-cause e#)
         NotFound       :next
         AmbiguousMatch :>> (fn [c#] (handle-ambiguous (.getObj c#)))
         (do (.printStackTrace e#) 
             (handle-error (.getMessage e#)))))))

(defn catching 
  "Decorator that wraps func in catch-all macro."
  [func]
  (fn [& args] (catch-all (apply func args))))

(defmacro defn+catch
  "Like defn, but wraps body in catch-all."
  [nam & args]
  `(do
    (defn ~nam ~@args)
    (decorate-with catching ~nam)))

(defmulti return-with-content-type first)

  (defmethod return-with-content-type :xml
    [[_ doc]]
    [{:headers {"Content-Type" "application/xml;charset=UTF-8"}} doc])
  
  (defmethod return-with-content-type :html
    [[_ doc]]
    [{:headers {"Content-Type" "application/xhtml+xml;charset=UTF-8"}} doc])
  
  (defmethod return-with-content-type :text
    [[_ doc]]
    [{:headers {"Content-Type" "text/plain;charset=UTF-8"}} doc])
                      
(defn handle-doc
  "Returns document with appropriate HTTP header."
  [doc]
  (let [as-str #(if (string? %) % (str %))
        doc    (if (coll? doc) (map as-str doc) (as-str doc))]
    (return-with-content-type
      (if (coll? doc)
        [:text (json-str doc)]
        (let [len   (.length doc)
              head  (.substring doc 0 
                      (if (> 100 len) len 100))]
          (cond
            (.contains head "<?")
                [:xml doc]
            (.contains head "<TEI")
                [:xml doc]
            (.contains head "<html")
                [:html doc]
            (.contains head "<span")
                [:html doc]
            :else
                [:text doc]))))))
