package com.example.expensemate.data;

/** The parts of an earlier transaction that suggestions for a new one are drawn from. */
public class PastTransaction {
    public long id;
    public String receiverName;
    public String smsSender;
    public String transactionType;
    public String category;
    public boolean needsReview;
    public Long accountId;
    public Long realAccountId;
    public Long linkedRecurringPaymentId;
}
