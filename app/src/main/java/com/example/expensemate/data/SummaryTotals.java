package com.example.expensemate.data;

/** One period's money: income and spending leave transfers out and report them separately. */
public class SummaryTotals {
    public String currency;
    public double expense;
    public double income;
    public double movedOut;
    public double movedIn;

    /** Money in minus money out. Transfers cancel out across all accounts but matter for just one. */
    public double left() {
        return income + movedIn - expense - movedOut;
    }
}
