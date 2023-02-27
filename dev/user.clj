(ns user
  (:require [portal.api :as p]))

(set! *warn-on-reflection* true)
(def p (p/open {:launcher :vs-code}))

(add-tap #'p/submit) ; Add portal as a tap> target