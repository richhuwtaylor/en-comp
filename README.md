# en-comp

An energy price comparison tool which:

- Prices plans on the market according to how much energy is consumed.
- Calculates how much energy is used according to how much a customer 
spends each month on a specific plan.

## Requirements

1. Ensure that Java is installed.
2. Ensure that [Leiningen](https://leiningen.org/) is installed.

## Usage

The tool takes the path to a json file containing an array of energy price plans with the format

    [
        {
            "supplier":
            "sse",
            "plan":
            "standard",
            "rates":
            [
                {"price": 13.5, "threshold": 150},
                {"price": 11.1, "threshold": 100},
                {"price": 10}
            ],
            "standing_charge": 9
        }
    ...
    ]

Plans contain a set of rates that describe how much (pence) the customer will be charged
for each kilowatt-hour (kWh) of energy that they use. Additionally, plans may
also include a daily standing charge.

To use the tool, from the project root, run either

    ./bin/comparison /path/to/plans.json    

or

    lein trampoline run /path/to/plans.json

#### Commands

- help: Print help message.
- exit: Exit the program.
- price [CONSUMPTION]: Produce an annual price (pounds, inclusive of VAT) for all plans available on the market, sorted by cheapest first.
- usage [SUPPLIER_NAME PLAN_NAME SPEND]: For the specified plan from a supplier, calculate how much energy (kWh) would be used annually 
  from a monthly spend in pounds (inclusive of VAT).
 

## Running the tests

To run the tests of the core functionality, use `lein test`.
