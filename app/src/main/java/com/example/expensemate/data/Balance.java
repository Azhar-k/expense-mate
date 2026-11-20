package com.example.expensemate.data;

/** What one fund or one account holds in one currency. Fund and account ids are separate, so never mix the two lists. */
public class Balance {
    public long id;
    public String currency;
    public double balance;
}
