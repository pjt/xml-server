(use 'compojure)

;; Load app/... code
(require 'xml-service)

(defserver server
  {:port 8080}
  "/*" (servlet xml-service/xml-server))

(start server)
