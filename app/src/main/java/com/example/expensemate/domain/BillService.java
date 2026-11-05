package com.example.expensemate.domain;

import androidx.lifecycle.LiveData;

import com.example.expensemate.data.RecurringPayment;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Bills and other recurring payments. */
public interface BillService {
    LiveData<List<RecurringPayment>> bills();

    LiveData<Double> totalAmount();

    LiveData<Double> remainingAmount();

    CompletableFuture<Void> add(RecurringPayment bill);

    CompletableFuture<Void> update(RecurringPayment bill);

    CompletableFuture<Void> delete(RecurringPayment bill);

    CompletableFuture<Void> markPaid(RecurringPayment bill);

    CompletableFuture<Void> resetPaid(RecurringPayment bill);

    /** Moves the bill's amount between its two accounts and marks it paid, both or neither. */
    CompletableFuture<Void> payByTransfer(RecurringPayment bill);
}
