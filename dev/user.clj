(ns user
  (:require [com.widdindustries.tiado-cljs2 :as util]
            [fetch-test-server]))

(defn run-test-ci [_]
  (let [server (fetch-test-server/run-test-server nil)]
    (util/tests-ci-shadow {:compile-mode :release})
    (.stop server)))

(defn test-watch []
  (util/browser-test-build :watch {}))

(comment
  
  (fetch-test-server/run-test-server nil)

  ; start up live-compilation of tests
  (test-watch)
  ; run cljs tests, having opened browser at test page (see print output of above "for tests, open...")
  (util/run-tests)
  ; start a cljs repl session in the test build. :cljs/quit to exit
  (util/repl :browser-test-build)
  ; run tests in headless browser
  (util/compile-and-run-tests-headless* :release)

  (util/stop-server)

  )