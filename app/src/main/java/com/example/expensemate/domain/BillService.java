package com.example.expensemate.domain;

import androidx.lifecycle.LiveData;

import com.example.expensemate.data.BillPayment;
import com.example.expensemate.data.RecurringPayment;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Bills and other recurring payments, each paid, skipped or owed month by month. */
public interface BillService {
    LiveData<List<RecurringPayment>> bills();

    /** Every paid and skipped month of every bill. */
    LiveData<List<BillPayment>> payments();

    LiveData<Double> totalAmount();

    LiveData<Double> remainingAmount();

    /** A new bill is owed from this month on. */
    CompletableFuture<Void> add(RecurringPayment bill);

    CompletableFuture<Void> update(RecurringPayment bill);

    /** Its months go with it; transactions linked to it stay. */
    CompletableFuture<Void> delete(RecurringPayment bill);

    /** {@code period} is a {@link com.example.expensemate.util.Periods} month. */
    CompletableFuture<Void> markPaid(RecurringPayment bill, int period);

    /** Not needed that month, so it is neither owed nor missed. */
    CompletableFuture<Void> skip(RecurringPayment bill, int period);

    /** Back to owed for that month. */
    CompletableFuture<Void> resetPaid(RecurringPayment bill, int period);

    /** Moves the bill's amount between its two funds and marks that month paid, both or neither. */
    CompletableFuture<Void> payByTransfer(RecurringPayment bill, int period);
}
