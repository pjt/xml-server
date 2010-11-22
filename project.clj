(defproject xml-server "0.1-SNAPSHOT"
            :description "XML document server, SVN-editor front-end."
            ;:main xml-service.run
            :namespaces [xml-service.run]
            :jvm-opts ["-server","-Xmx256m"]
            :dependencies [[org.clojure/clojure "1.2.0"]
                           [org.clojure/clojure-contrib "1.2.0"]
                           [compojure "0.3.2"]
                           [clojure-saxon "0.9.1-SNAPSHOT"]])
            ;:dev-dependencies [[lein-clojars "0.5.0-SNAPSHOT"]
            ;                   [lein-war "0.0.1-SNAPSHOT"]])
