(ns xml-service.repos-config
  (:use clojure.contrib.def))

(defvar local-repos (.getCanonicalPath (java.io.File. "../sa-repos"))
  "Root of the local checkout of the SVN repository, from which XML entries
  are loaded & which is kept updated with repository changes on checkin. 
  (The latter must be implemented on the SVN server.)")

(defvar remote-repos "http://svn.hdwdev.artsci.wustl.edu/spenser/"
  "URL for the root of the SVN repository, used for authenticating, checking
  files in & out, &c.")
