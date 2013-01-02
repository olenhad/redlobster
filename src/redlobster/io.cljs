(ns redlobster.io
  (:require-macros [cljs.node-macros :as n])
  (:require [redlobster.promise :as p]
            [redlobster.stream :as s]
            [redlobster.http :as http])
  (:use [cljs.node :only [log]]
        [cljs.yunoincore :only [clj->js]])
  (:use-macros [redlobster.macros :only [let-realised waitp]]))

(n/require "url" url)

(defn parse-url [path]
  (.parse url path))

(defn http-url? [path]
  (= "http:" (.-protocol (parse-url path))))

(defn file-url? [path]
  (let [p (parse-url path)]
    (and (or
          (= "file:" (.-protocol p))
          (not (.-protocol p)))
         (or
          (= "" (.-host p))
          (not (.-host p))))))

(defn- slurp-http [path]
  (let-realised [res (http/request url)]
                (s/read-stream @res)))

(defn- slurp-file [path]
  (s/read-stream (s/read-file path)))

(defn slurp [path]
  (cond
   (http-url? path) (slurp-http path)
   (file-url? path) (slurp-file path)
   :else (p/promise-fail {:redlobster.io/unknown-path path})))

(defn- http-success? [res]
  (let [status (.-statusCode res)]
    (and (>= status 200)
         (< status 300))))

(defn- spit-http [path data]
  (let [o (parse-url path)]
    (aset path "method" "PUT")
    (waitp (http/request o data)
           #(if (http-success? %)
              (realise nil)
              (realise-error {:redlobster.http/status-code (.-statusCode %)}))
           #(realise-error %))))

(defn- spit-file [path data]
  (s/write-stream (s/write-file path) data))

(defn spit [path data]
  (cond
   (http-url? path) (spit-http path data)
   (file-url? path) (spit-file path data)
   :else (p/promise-fail {:redlobster.io/unknown-path path})))
