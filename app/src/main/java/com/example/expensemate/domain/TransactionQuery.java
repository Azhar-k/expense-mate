package com.example.expensemate.domain;

/** Which transactions a list asks for. A null field matches any row; the dates are inclusive. */
public class TransactionQuery {
    public long fromMillis;
    public long toMillis;
    public Long accountId;
    public String search;
    public String description;
    public String receiver;
    public String category;
    public Double amount;
    public String currency;
    public String transactionType;
    public Boolean excluded;
    public Long billId;
    public Boolean transfers;
    public Boolean needsReview;
}
