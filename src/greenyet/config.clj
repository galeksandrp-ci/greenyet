(ns greenyet.config
  (:require [clj-yaml.core :as yaml]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def config-dir (System/getenv "CONFIG_DIR"))

(def polling-interval-in-ms (or (some-> (System/getenv "POLLING_INTERVAL")
                                        Integer/parseInt)
                                (some-> (System/getenv "TIMEOUT")
                                        Integer/parseInt)
                                5000))

(def ^:private config-params [["CONFIG_DIR" (or config-dir
                                                "")]
                              ["POLLING_INTERVAL" polling-interval-in-ms]
                              ["PORT" (or (System/getenv "PORT")
                                          3000)]])

(defn config-params-as-string []
  (->> config-params
       (map (fn [[env-var value]]
              (format "  %s: '%s'" env-var value)))
       (str/join "\n")))


(def development? (= "development" (System/getProperty "greenyet.environment")))


(defn- status-url [host {url-template :url}]
  (str/replace url-template #"%hostname%" (:hostname host)))

(defn with-config [host status-url-config]
  (let [host-config (first (filter #(= (:system %) (:system host)) status-url-config))
        url (status-url host host-config)]
    (assoc host
           :status-url url
           :config host-config)))

(defn- validate-hosts [host-lists]
  (let [hosts-with-checks (map (fn [host]
                                 [host (cond
                                         (not (contains? host :hostname)) "Missing 'hostname'"
                                         (not (contains? host :system)) "Missing 'system'")])
                               host-lists)
        successful-hosts (->> hosts-with-checks
                              (filter (fn [[host checks]] (nil? checks)))
                              (map first))
        errors (->> hosts-with-checks
                    (filter (fn [[host checks]] (not (nil? checks))))
                    (map (fn [[host checks]] (format "%s for entry %s" checks host))))]
    [successful-hosts errors]))

(defn validate-status-url-config [status-url-entries]
  (let [entries-with-checks (map (fn [entry]
                                   [entry (cond
                                            (not (contains? entry :system)) "Missing 'system'"
                                            (not (contains? entry :url)) "Missing 'url'")])
                                 status-url-entries)
        successful-entries (->> entries-with-checks
                                (filter (fn [[entry checks]] (nil? checks)))
                                (map first))
        errors (->> entries-with-checks
                    (filter (fn [[entry checks]] (not (nil? checks))))
                    (map (fn [[entry checks]] (format "%s for entry %s" checks entry))))]
    [successful-entries errors]))

(defn- parse-from-yaml [build-file file-name]
  (let [config-file (build-file config-dir file-name)]
    (yaml/parse-string (slurp config-file))))


(defn hosts-with-config [& [build-file]]
  (let [parse (fn [file-name] (parse-from-yaml (or build-file
                                                   io/file)
                                               file-name))
        [host-list hosts-errors] (validate-hosts (parse "hosts.yaml"))
        [status-url-entries status-url-errors] (validate-status-url-config (parse "status_url.yaml"))
        good-hosts-with-config (->> host-list
                                    (map #(with-config % status-url-entries))
                                    (map-indexed (fn [idx host]
                                                   (assoc host :index idx))))]
    [good-hosts-with-config (concat hosts-errors
                                    status-url-errors)]))
