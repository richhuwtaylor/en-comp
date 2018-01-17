(ns en-comp.core-test
  (:require [clojure.test :refer :all]
            [en-comp.core :refer :all]))

(def test-state
  [{:supplier "eon", :plan "variable", :rates [{:price 13.5, :threshold 100} {:price 10}]}
   {:supplier "ovo", :plan "standard", :rates [{:price 12.5, :threshold 300} {:price 11}]}
   {:supplier "edf", :plan "fixed", :rates [{:price 14.5, :threshold 250} {:price 10.1, :threshold 200} {:price 9}]}
   {:supplier "bg", :plan "standing-charge", :rates [{:price 9}], :standing-charge 7}])

(def expected-prices
  [{:supplier "eon", :plan "variable", :total-cost 10867.5}
   {:supplier "edf", :plan "fixed", :total-cost 11124.75}
   {:supplier "ovo", :plan "standard", :total-cost 12022.5}
   {:supplier "bg", :plan "standing-charge", :total-cost 12132.75}])

(deftest test-price
  (testing "test-price"
    (let [result (vec (price test-state 1000))]
      (testing "Produces the expected list of plans."
        (is (= expected-prices result)))
      (testing "Plan maps contain the required keys."
        (is (= (keys (first result)) [:supplier :plan :total-cost])))
      (testing "Plans are sorted by total-cost with the cheapest first."
        (let [plan-costs (map #(get % :total-cost) result)]
          (is (= (sort plan-costs) plan-costs)))))))

(deftest test-plan-cost
  (testing "test-plan-cost"
    (testing "Adds the correct standing charge to the cost when appropriate."
      (is (= (plan-cost {:supplier "bg", :plan "standing-charge", :rates [{:price 9}], :standing-charge 7} 1000) 12132.75))
      (is (= (plan-cost {:supplier "bg", :plan "standing-charge", :rates [{:price 9}]} 1000) 9450.0)))))

(deftest test-rates-cost
  (testing "test-rates-cost"
    (testing "Returns the expected cost for the supplied rates and usage"
      (is (= (rates-cost (:rates (first test-state)) 10350.0))))))

(deftest test-usage
  (testing "test-usage"
    (testing "Correctly factors in the standing change of a plan when appropriate."
      (is (= (int (usage {:supplier "bg", :plan "standing-charge", :rates [{:price 9}], :standing-charge 7} 350))
             44160))
      (is (= (int (usage {:supplier "bg", :plan "standing-charge", :rates [{:price 9}]} 350))
             44444)))))

(deftest test-rates-usage
  (testing "test-rates-usage"
    (testing "Returns the expected usage for the supplied rates and cost."
      (is (= (int (rates-usage (:rates (first test-state)) 100000)) 9965)))))