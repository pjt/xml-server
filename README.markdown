XML-Server
==========

Last updated: Thu 07 May 2009 

Acts as a front-end to a SVN repository of XML & XSL files. Maintains up-to-date copies
of the files as the repository changes, allows reading of XML files optionally passed 
through stylesheets, allows editing of XML files.

The primary entities the server knows about are the XML files; the XSL files simply act as
filters on the former, & can't be accessed directly. 

Installation
------------

To install, check out a local copy of the repository. Record its location in the

      app/xml_service/repos_config.clj

file, as well as the location of the remote repository against which commits are to be
made.

To run the server, invoke

      ./run

(optionally with the `-r` option, to bring up a REPL). The server will listen on port 8080
(this can be changed in the `boot.clj` driver file).


API, by URI
-----------

 * `/`

    GET.  
    Returns human-readable listing of XML, XSL entries currently in the repository.

 * `/list`

    GET.  
    Returns JSON object with two keys: `xml` & `xsl`. The value of `xml` is an object
    containing all XML entries (keyed by full path in the repository); the value of
    `xsl` is an array of XSL entries.

 * `/list/:xml`

    GET.  
    Returns JSON object containing one key: the full repository path for XML file, whose
    value is an array of explicitly declared (in the "spenser-stylesheets" processing
    instruction) XSL entries.

 * `/xml/:xml(?xquery)`

    GET.  
    Returns XML filed named, possibly filtered by an XQuery expression. If the result of 
    evaluating the XQuery expression is a collection (of nodes or values), the collection is 
    returned as a JSON array.

 * `/style/:style/:xml(?xquery)`

    GET.  
    Returns the XML file named, run through the stylesheet(s) named, possibly filtered
    by an XQuery expression. The XQuery expression is evaluated against the XML document; if
    its result is a collection, the stylesheet(s) are applied to each. If the result of 
    stylesheet application is a collection, it's returned as a JSON array.

 * `/edit/:style/:xml`

    GET. Requires login (authenticated against repository).  
    Returns XML file named, run through stylesheet(s) named. Makes XML file the currently-edited
    document for the session, thus making it the target for future edits.

 * `/edit`

    POST. Requires login.  
    PARAMETERS:
      * `select`: XPath for node to be modified
      * `update`: string representing new state of `select` node
      * `update-style`: name of stylesheet(s) through which to run `update` before updating
      * `post-style`: name of stylesheet(s) through which to run returned node (parent of `select`)

    Replaces the node identified by `select` with the value of `update` (possibly run through
    `update-style` stylesheet(s)) in the session's currently-edited document. Returns the parent 
    of `select`, or, if `select` is the root, the root.

 * `/edit/diff`

    GET. Requires login.  
    Returns `svn diff` output reflecting changes from the repository for currently-edit document.

 * `/edit/commit`

    POST. Requires login.  
    PARAMETERS:
      * `msg`: log message for check-in

    Commits edits made to session's currently-edited document back to the repository.


Naming XML, XSL entries
-----------------------

For the above URLs, the keywords :xml & :style represent holes in the paths where names of 
particular files go. The naming scheme is as follows:

   * for XML files: 

      1. the full path of the file in the repository, e.g.
          
               works/volOne/trunk/letters/letters.xml

         This is the canonical name for the XML, & is what's returned by the `/list` URLs.

      1. any substring of the above that uniquely identifies a file, e.g.

               letters

         This is to support interactive use at a browser. If the susbstring doesn't
         uniquely identify a file, an HTTP 404 is returned whose body lists all the files
         matched.

   * for XSL files:

      XSL files don't have canonical names; their names are simply filenames minus extension.
      They're resolved relative to the XML file they'll be applied to, in this resolution order:
      
      1. If the name is a uniquely identifying substring of a stylesheet listed in the 
         XML file's `spenser-stylesheets` processing instruction, the stylesheet in the
         processing-instruction is chosen. E.g. `choices` is given in the URL, `choices_fq`
         exists in the XML's processing instruction, `choices_fq` is chosen.

      1. If 1. isn't true, all the stylesheets in the repository for which the name is a substring
         are collected. If the collection contains one stylesheet, it's chosen; if there's
         more than one, the stylesheet closest to the XML file is chosen, where distance equals 
         number of directory steps between XML file & stylesheet (steps *beneath* the XML file's
         containing directory are weighted at half of steps outside containing directory). If
         there is a single closest stylesheet, it's chosen; if there's not, an HTTP 404 is
         returned whose body lists the closest sheets.

      The rule of thumb, then, for arranging stylesheets in the repository is: put stylesheets
      meant for arbitrary XML input in a single "global" directory, & put stylesheets meant for
      particular files either in the same directory as, or in a directory beneath, the file (&,
      optionally, point directly to it in the processing instruction).

      Finally, names of stylesheets can specify multiple sheets. Combine sheet names with the `+`
      character. E.g., to apply a tokenizing sheet & an HTML-conversion sheet, hit the

          /style/tokenize+html/letters

      URL. Sheets are applied in left-to-right order.
      

