package com.example.expensemate.viewmodel;

import com.example.expensemate.domain.TransactionQuery;

import java.util.Calendar;

/** What the Activity list is showing. A new instance replaces the old one on every change. */
public class TransactionFilter {
    public long fromMillis;
    public long toMillis;
    public String rangeLabel;
    public Long accountId;
    public String search;
    public String description;
    public String receiver;
    public String category;
    public Double amount;
    public String transactionType;
    public Boolean excluded;
    public Long billId;
    public Boolean transfers;
    public Boolean needsReview;

    public static TransactionFilter lastDays(int days) {
        TransactionFilter filter = new TransactionFilter();
        filter.setLastDays(days);
        return filter;
    }

    public void setLastDays(int days) {
        Calendar end = Calendar.getInstance();
        endOfDay(end);
        Calendar start = (Calendar) end.clone();
        start.add(Calendar.DAY_OF_MONTH, -days);
        startOfDay(start);
        fromMillis = start.getTimeInMillis();
        toMillis = end.getTimeInMillis();
        rangeLabel = "Last " + days + " days";
    }

    public void setAllTime() {
        fromMillis = 0;
        toMillis = Long.MAX_VALUE;
        rangeLabel = "All time";
    }

    public TransactionFilter copy() {
        TransactionFilter copy = new TransactionFilter();
        copy.fromMillis = fromMillis;
        copy.toMillis = toMillis;
        copy.rangeLabel = rangeLabel;
        copy.accountId = accountId;
        copy.search = search;
        copy.description = description;
        copy.receiver = receiver;
        copy.category = category;
        copy.amount = amount;
        copy.transactionType = transactionType;
        copy.excluded = excluded;
        copy.billId = billId;
        copy.transfers = transfers;
        copy.needsReview = needsReview;
        return copy;
    }

    public TransactionQuery toQuery() {
        TransactionQuery query = new TransactionQuery();
        query.fromMillis = fromMillis;
        query.toMillis = toMillis;
        query.accountId = accountId;
        query.search = search;
        query.description = description;
        query.receiver = receiver;
        query.category = category;
        query.amount = amount;
        query.transactionType = transactionType;
        query.excluded = excluded;
        query.billId = billId;
        query.transfers = transfers;
        query.needsReview = needsReview;
        return query;
    }

    /** True when any of the filters behind the "More" chip is on. */
    public boolean hasMoreFilters() {
        return description != null || receiver != null || amount != null || transactionType != null
                || excluded != null || billId != null || transfers != null || needsReview != null;
    }

    /** True when the list differs from the default view of the last 30 days. */
    public boolean isCustomized() {
        return !"Last 30 days".equals(rangeLabel) || accountId != null || search != null || category != null
                || hasMoreFilters();
    }

    public static void startOfDay(Calendar day) {
        day.set(Calendar.HOUR_OF_DAY, 0);
        day.set(Calendar.MINUTE, 0);
        day.set(Calendar.SECOND, 0);
        day.set(Calendar.MILLISECOND, 0);
    }

    public static void endOfDay(Calendar day) {
        day.set(Calendar.HOUR_OF_DAY, 23);
        day.set(Calendar.MINUTE, 59);
        day.set(Calendar.SECOND, 59);
        day.set(Calendar.MILLISECOND, 999);
    }
}
