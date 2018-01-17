(ns en-comp.core
  (:gen-class)
  (:require
    [clojure.java.io :as io]
    [clojure.string :as str]
    [cheshire.core :as ches]))


(def vat-mult 1.05)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UTILS

(defn underscore->hyphen
  [s]
  (str/replace s #"_" "-"))

(defn string->keyword
  [s]
  (-> s
      (underscore->hyphen)
      (keyword)))

(defn monthly-pounds-vat->annual-pence
  [spend]
  (/ (* 1200 spend) vat-mult))

(defn parse-line
  [line]
  (mapv #(some identity %)
        (map rest (re-seq #"'([^']+)'|\"([^\"]+)\"|(\S+)" line))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; IO

;; doall needed to realize the whole return value before with-open returns
;; (else see a IOException Stream closed)
(defn read-json
  "Returns a vector of maps of plan details."
  [path]
  (with-open [rdr (io/reader path)]
    (doall (-> rdr
               (ches/parsed-seq string->keyword)
               (first)))))

(defn render-plan
  [{:keys [supplier plan total-cost]}]
  (println (str/join "," [supplier plan (format "%.2f" (/ total-cost 100))])))

(defn render-plans
  [plans]
  (doseq [plan plans] (render-plan plan)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; PRICE COMPARISON

;; First implementation of rates-cost using recursion.
(defn rates-cost-recur
  "Returns the cost (pence) of a plan's rates for the supplied usage (kWh) and rates excl. VAT."
  [rates usage]
  (loop [rates rates remaining-usage usage bill 0]
    (if (zero? remaining-usage)
      bill
      (let [{:keys [price threshold]} (first rates)]
        (if-not threshold
          (+ bill (* remaining-usage price))
          (let [n (min threshold remaining-usage)]
            (recur (rest rates) (- remaining-usage n) (+ bill (* n price)))))))))

;; Second implementation of rates-cost using reduce - slightly nicer
(defn rates-cost
  "Returns the cost (pence) of a plan's rates for the supplied usage (kWh) and rates excl. VAT."
  [rates usage]
  (reduce (fn [{:keys [remaining-usage bill]} {:keys [price threshold]}]
            (let [n (if threshold (min threshold remaining-usage) remaining-usage)
                  remaining-usage (- remaining-usage n)
                  bill (+ bill (* n price))]
              (if (zero? remaining-usage)
                (reduced bill)
                {:remaining-usage remaining-usage :bill bill})))
          {:remaining-usage usage :bill 0}
          rates))

(defn plan-cost
  "Returns the total annual cost (pence) of the plan for the supplied usage incl. VAT."
  [{:keys [rates standing-charge]} usage]
  (let [rates-cost (rates-cost rates usage)]
    (* vat-mult (if standing-charge
                  (+ rates-cost (* 365 standing-charge))
                  rates-cost))))

(defn price
  "Returns a collection of plans in ascending order of annual cost (in pence) for the supplied annual usage."
  [plans usage]
  (->> plans
       (map #(assoc (select-keys % [:supplier :plan]) :total-cost (plan-cost % usage)))
       (sort-by :total-cost)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ANNUAL USAGE

;; Quick and dirty version, will slightly overestimate the number of kWh due to use of keep over range
(defn rates-usage-keep
  "Returns the usage (kWh) needed to incur the given annual cost in pence (excl. VAT) for the given rates."
  [rates cost]
  (first (keep (fn [i] (when (>= (rates-cost rates i) cost) i)) (range))))

;; Second iteration of rates-usage using recursion
(defn rates-usage-recur
  "Returns the usage (kWh) needed to incur the given annual cost in pence (excl. VAT) for the given rates."
  [rates cost]
  (loop [rates rates remaining-cost cost total-usage 0]
    (if (zero? remaining-cost)
      total-usage
      (let [{:keys [price threshold]} (first rates)]
        (if-not threshold
          (+ total-usage (/ remaining-cost price))
          (let [n (min (* threshold price) remaining-cost)]
            (recur (rest rates) (- remaining-cost n) (+ total-usage (/ n price)))))))))

(defn rates-usage
  "Returns the usage (kWh) needed to incur the given annual cost in pence (excl. VAT) for the given rates."
  [rates cost]
  (reduce (fn [{:keys [remaining-cost total-usage]} {:keys [price threshold]}]
            (let [n (if threshold (min (* threshold price) remaining-cost) remaining-cost)
                  remaining-cost (- remaining-cost n)
                  total-usage    (+ total-usage (/ n price))]
              (if (zero? remaining-cost)
                (reduced total-usage)
                {:remaining-cost remaining-cost :total-usage total-usage})))
          {:remaining-cost cost :total-usage 0}
          rates))

(defn usage
  "Returns the annual usage (kWh) needed to incur the supplied monthly cost (pounds incl. VAT) for the given plan."
  [plan monthly-spend]
  (let [annual-spend (monthly-pounds-vat->annual-pence monthly-spend)
        energy-spend (if-let [charge (:standing-charge plan)]
                       (- annual-spend (* 365 charge))
                       annual-spend)]
    (rates-usage (:rates plan) energy-spend)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; HELP

(def help-string
  (str/join
    "\n" [""
          "Commands:"
          ""
          "help: Print this help message."
          "exit: Exit the program."
          "price [CONSUMPTION]: Produce an annual price (inclusive of VAT) for all plans available on the market, sorted by cheapest first."
          "usage [SUPPLIER_NAME PLAN_NAME SPEND]: For the specified plan from a supplier, calculate how much energy would be used annually from a monthly spend in pounds (inclusive of VAT)."]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; COMMANDS

(defmulti handle-command (fn [command _ _] command))

(defmethod handle-command "help" [_ _ _] (println help-string))

(defmethod handle-command "exit" [_ _ _] ::exit)

(defmethod handle-command "price"
  [_ args state]
  (if (not= (count args) 1)
    (println "Wrong number of arguments for command: price")
    (let [consumption (read-string (first args))]
      (if (not (integer? consumption))
        (println "Please provide an integer annual consumption (kWh)")
        (render-plans (price state consumption))))))

(defmethod handle-command "usage"
  [_ args state]
  (if (not= (count args) 3)
    (println "Wrong number of arguments for command: usage")
    (let [[supplier plan spend] args
          spend (read-string spend)]
      (if-let [matching-plan (first (filter #(and (= (:supplier %) supplier) (= (:plan %) plan)) state))]
        (println (format "%.0f" (usage matching-plan spend)))
        (println (str/join " " ["No" plan "energy plan found for supplier" supplier]))))))

(defmethod handle-command :default
  [command _ _]
  (println "Unrecognised command:" command))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; INIT

(defn init
  "Load energy plans from json."
  [path]
  (read-json path))

(defn -main
  [& args]
  (println "Reading energy plans from json...")
  (loop [state (init (first args))]
    (println "\n\nReady for command:")
    (let [[command & args] (parse-line (read-line))
          result (handle-command command args state)]
      (if (= result ::exit)
        (println "Bye.")
        (recur state)))))
