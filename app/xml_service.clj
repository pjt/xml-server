(ns xml-service
  (:require [xml-service.repos-config :as config])
  (:use 
    [xml-service edit http-utilities utilities entries paths]
    [clojure.contrib.json.write :only (json-str)]
    [saxon :only (compile-string serialize)]
    compojure
    clojure.contrib.server-socket))

;; Doc refs

(def xml-entries (ref {}))
(def xsl-entries (ref {}))

(defn update-entries []
  (dosync 
      (commute xml-entries get-xml-entries)
      (commute xsl-entries get-xsl-entries)))

(update-entries)

;; Tempfile cleanup

(defn cleanup
  [ag rf old-sesh new-sesh]
  (when (not= @(:svn-co-file old-sesh) @(:svn-co-file new-sesh))
    (send ag (fn [ag-state] 
               (let [old-co (co-path-instance-root @(:svn-co-file old-sesh))]
                  (delete-rec old-co) 
                  (conj ag-state old-co))))))

;; Route funcs

(defn trimming-serve-file
  "Attempts to serve file, trimming directory components left-to-right
  if file not found."
  ([path] (trimming-serve-file "public" path))
  ([fs-root path]
    (or (serve-file fs-root path)
      (loop [paths (map #(reduce-path-by path %) (reverse (dirname-seq path)))]
        (when (seq paths)
          (or (serve-file fs-root (first paths))
            (recur (rest paths)))))
     :next)))

(defn get-json-info
  "Get info in JSON about XML entry."
  ([] (json-str {"xml" (into {} (for [xml (vals @xml-entries)] 
                              [(:repos-path xml) (:stylesheets xml)]))
             "xsl" (map :name (vals @xsl-entries))}))
  ([q] (let [xml (find-xml @xml-entries q)] 
         (json-str {(:repos-path xml) (:stylesheets xml)}))))

(defn- maybe-apply-query
  [xmldoc xquery]
  (if xquery (apply-xquery xquery xmldoc) xmldoc))

(defn- find-multiple-xsl
  "Takes xsl query – a plus-sign-separated series of stylesheet names –
  and returns a function that applies them to a node.

  Note: stylesheets are applied in left-to-right order, e.g. the query
  \"tokenize+html\" returns function that applies the \"tokenize\"
  stylesheet, then the \"html\" stylesheet."
  [xml-entry xsl-entries #^String query]
  (let [names (.split query "\\+")
        found (map #(find-xsl xml-entry xsl-entries %) names)]
    (apply comp (reverse (map :fn found)))))

(defn- apply-xsl
  [xsl-fn xmldoc] 
  (if (coll? xmldoc) (map xsl-fn xmldoc), (xsl-fn xmldoc)))

(defn retrieve-doc
  "Retrieve XML document, possibly filtered by an XQuery expression, possibly
  rendered through a stylesheet."
  ([q xquery] 
   (let [xml (find-xml @xml-entries q)]
     (handle-doc (maybe-apply-query (:doc xml) xquery))))
  ([q-xml xquery q-xsl] 
    (let [xml     (find-xml @xml-entries q-xml)
          ddoc    (maybe-apply-query (:doc xml) xquery)
          xsl-fn  (find-multiple-xsl xml @xsl-entries q-xsl)]
      (handle-doc (apply-xsl xsl-fn ddoc)))))

(defn retrieve-for-edit
  "Retrieve & return XML document, run through the editing stylesheet. 
  Also, as side-effects, check out file to filesystem, associate document
  & checkout location in session map."
  [session request q-xml q-xsl]
  (let [xml     (find-xml @xml-entries q-xml)
        xsl-fn  (find-multiple-xsl xml @xsl-entries q-xsl)
        co-file (future (svn-co config/remote-repos
                  (:repos-path xml) (make-co-path-root (:id session))
                    (:svn-user session) (:svn-pword session)))]
;    (dosync
;      (alter session #(if-not (:cleanup-agent %) 
;                         (assoc % :cleanup-agent (agent []))
;                         %))
;      (commute session #(assoc % :editing-doc xml :svn-co-file co-file)))
;    (add-watch session (:cleanup-agent session) cleanup)
    [(session-assoc :editing-doc xml :svn-co-file co-file)
     (handle-doc (xsl-fn (:doc xml)))]))

(defn make-edit
  "Apply an edit to the currently edited document (:editing-doc).
  Update reference in session map, re-serialize doc to checkout location,
  and return the HTML for the edited node's parent."
  [session select update update-style post-style]
  (let [xml     (:editing-doc session) 
        update  (if update-style 
                  (str ((find-multiple-xsl xml @xsl-entries update-style)
                          (compile-string update)))
                  update)
        post-fn (if post-style
                  (find-multiple-xsl xml @xsl-entries post-style)
                  identity)
        edited  (apply-edit (-> session :editing-doc :doc) select update)]
    (future (serialize edited @(:svn-co-file session) {:indent "yes"}))
    [(alter-session assoc-in [:editing-doc :doc] edited)
     (handle-doc (post-fn (return-parent edited select)))]))
;                  identity)]
;    (dosync (commute session #(assoc-in % [:editing-doc :doc]
;                                (apply-edit (-> % :editing-doc :doc)
;                                  select update))))
;    (let [ddoc (-> session :editing-doc :doc)]
;      (future (serialize ddoc @(:svn-co-file session)))
;      (handle-doc (post-fn (return-parent ddoc select))))))

(defn commit
  "Commit changes from checked-out file."
  [session svn-co-file msg]
  (svn-up svn-co-file (:svn-user session) (:svn-pword session))        
  (svn-ci svn-co-file msg (:svn-user session) (:svn-pword session)))

(defn diff
  "Return svn diff on checked-out file."
  [svn-co-file]
  (svn-diff svn-co-file))

(defn svn-login
  "Authenticate against remote SVN repository."
  [session params]
  (if (svn-auth config/remote-repos (params :svn-user) (params :svn-pword))
;    (do 
;      (dosync (commute session assoc :svn-user (params :svn-user)
;                                     :svn-pword (params :svn-pword)))
;      (redirect-to (or (params :edit-url) "/")))
    [(session-assoc :svn-user (params :svn-user) :svn-pword (params :svn-pword))
     (redirect-to (or (params :edit-url) "/"))]
    :next))

(decorate-with catching
      get-json-info
      retrieve-doc
      retrieve-for-edit
      make-edit
      commit
      diff
      svn-login)

;; Routes themselves

(defroutes xml-server "XML Server application."

  (GET "/"
    (templ "Repository Entries"
      [:p "XML entries"
        (unordered-list (for [xml (vals @xml-entries)]
                          (html (:repos-path xml)
                            (unordered-list (:stylesheets xml)))))]
      [:p "XSL entries"
        (unordered-list (for [xsl (vals @xsl-entries)] (:name xsl)))]))
                          

  (GET "/list"
    (get-json-info))

  (GET "/list/*"
    (get-json-info (params :*)))

  (GET "/update"
    (catch-all
      (svn-up config/local-repos)
      (update-entries) ""))
    
  (GET "/xml/*"
    (retrieve-doc (params :*) (params :xquery)))
  
  (GET "/style/:style/*"
    (retrieve-doc (params :*) (params :xquery) (params :style)))

  (POST "/edit/svn-login"
    (if (every? identity (map params [:svn-user :svn-pword]))
      (svn-login session params)
      :next))

  (ANY "/edit*" ; svn authenticate beyond this point
    (if (not (:svn-user session))
      (templ "Log in"
        (form-to [:post "/edit/svn-login"]
          (label "svn-user" "Username") " " (text-field "svn-user")
          (label "svn-pword" "Password") " " (password-field "svn-pword")
          (submit-button "Go")
          (hidden-field "edit-url" (or (params :edit-url) (:uri request)))))
      :next))

  (GET "/edit/:style/*"
    (retrieve-for-edit session request (params :*) (params :style)))

  (POST "/edit"
    (if (and (params :select) (-> session :editing-doc :doc))
      (make-edit session (params :select) (params :update) 
                            (params :update-style) (params :post-style))
      :next))

  (GET "/edit/diff"
    (if-let [co-file @(:svn-co-file session)]
      (diff co-file)
      :next))

  (POST "/edit/commit"
    (if (params :msg)
      (commit session @(:svn-co-file session) (params :msg))
      :next))

  (GET "/edit/bounce"
    ; TODO: these interfering with stuff at /edit/* -- investigate!
    #_(dosync
      (commute session dissoc :editing-doc :svn-co-file)) "")

  (GET "/open/local/socket/repl" (def sockrepl (create-repl-server 1116)) "")
  (GET "/static/*" (or (serve-file (str config/local-repos "/xmlserver-static") (params :*)) :next))
  (GET "*" (trimming-serve-file (:uri request)))
  (ANY "*" (page-not-found)))

(decorate xml-server (with-session {:type :memory :expires (* 8 60 60)}))

