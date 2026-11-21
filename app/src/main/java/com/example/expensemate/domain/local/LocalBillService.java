package com.example.expensemate.domain.local;

import androidx.lifecycle.LiveData;

import com.example.expensemate.data.BillPayment;
import com.example.expensemate.data.RecurringPayment;
import com.example.expensemate.data.Transaction;
import com.example.expensemate.data.Transfers;
import com.example.expensemate.data.store.BillStore;
import com.example.expensemate.data.store.TransactionStore;
import com.example.expensemate.domain.BillService;
import com.example.expensemate.util.Periods;

import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;

final class LocalBillService implements BillService {
    private final BillStore store;
    private final TransactionStore transactions;
    private final TransactionRules rules;
    private final Writer writer;

    LocalBillService(BillStore store, TransactionStore transactions, TransactionRules rules, Writer writer) {
        this.store = store;
        this.transactions = transactions;
        this.rules = rules;
        this.writer = writer;
    }

    @Override
    public LiveData<List<RecurringPayment>> bills() {
        return store.observeAll();
    }

    @Override
    public LiveData<List<BillPayment>> payments() {
        return store.observePayments();
    }

    @Override
    public LiveData<Double> totalAmount() {
        return store.observeTotalAmount();
    }

    @Override
    public LiveData<Double> remainingAmount() {
        return store.observeRemainingAmount();
    }

    @Override
    public CompletableFuture<Void> add(RecurringPayment bill) {
        if (bill.getStartPeriod() == 0) {
            bill.setStartPeriod(Periods.current());
        }
        return writer.run(() -> store.insert(bill));
    }

    @Override
    public CompletableFuture<Void> update(RecurringPayment bill) {
        return writer.run(() -> store.update(bill));
    }

    @Override
    public CompletableFuture<Void> delete(RecurringPayment bill) {
        return writer.run(() -> store.delete(bill));
    }

    @Override
    public CompletableFuture<Void> markPaid(RecurringPayment bill, int period) {
        return writer.run(() -> store.savePayment(BillPayment.paid(bill, period, new Date(), null)));
    }

    @Override
    public CompletableFuture<Void> skip(RecurringPayment bill, int period) {
        return writer.run(() -> store.savePayment(BillPayment.skipped(bill, period)));
    }

    @Override
    public CompletableFuture<Void> resetPaid(RecurringPayment bill, int period) {
        return writer.run(() -> store.deletePayment(bill.getId(), period));
    }

    @Override
    public CompletableFuture<Void> payByTransfer(RecurringPayment bill, int period) {
        Date now = new Date();
        Transaction[] legs = Transfers.newPair(bill.getAmount(), bill.getFromFundId(), bill.getToFundId(),
                now, bill.getName());
        legs[0].setLinkedRecurringPaymentId(bill.getId());
        return writer.run(() -> {
            // Both sides stay in one account that holds the bill's currency, so only fund balances move
            Long account = rules.accountIn(bill.getCurrency());
            for (Transaction leg : legs) {
                leg.setAccountId(account);
                rules.fillDefaults(leg);
                leg.setCurrency(bill.getCurrency());
                transactions.insert(leg);
            }
            store.savePayment(BillPayment.paid(bill, period, now, legs[0].getId()));
        });
    }
}
