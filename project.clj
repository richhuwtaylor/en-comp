(defproject en-comp "0.1.0-SNAPSHOT"
  :description "An energy price plan comparison tool."
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [cheshire "5.8.0"]]
  :main ^:skip-aot en-comp.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
