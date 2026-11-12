package com.example.expensemate.domain.local;

import androidx.lifecycle.LiveData;

import com.example.expensemate.data.RecurringPayment;
import com.example.expensemate.data.Transaction;
import com.example.expensemate.data.Transfers;
import com.example.expensemate.data.store.BillStore;
import com.example.expensemate.data.store.TransactionStore;
import com.example.expensemate.domain.BillService;

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
    public LiveData<Double> totalAmount() {
        return store.observeTotalAmount();
    }

    @Override
    public LiveData<Double> remainingAmount() {
        return store.observeRemainingAmount();
    }

    @Override
    public CompletableFuture<Void> add(RecurringPayment bill) {
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
    public CompletableFuture<Void> markPaid(RecurringPayment bill) {
        bill.setCompleted(true);
        bill.setLastCompletedDate(new Date());
        return update(bill);
    }

    @Override
    public CompletableFuture<Void> resetPaid(RecurringPayment bill) {
        bill.setCompleted(false);
        return update(bill);
    }

    @Override
    public CompletableFuture<Void> payByTransfer(RecurringPayment bill) {
        Date now = new Date();
        Transaction[] legs = Transfers.newPair(bill.getAmount(), bill.getFromAccountId(), bill.getToAccountId(),
                now, bill.getName());
        legs[0].setLinkedRecurringPaymentId(bill.getId());
        bill.setCompleted(true);
        bill.setLastCompletedDate(now);
        return writer.run(() -> {
            // Both sides stay in one real account that holds the bill's currency, so only virtual balances move
            Long real = rules.realAccountIn(bill.getCurrency());
            for (Transaction leg : legs) {
                leg.setRealAccountId(real);
                rules.fillDefaultAccounts(leg);
                leg.setCurrency(bill.getCurrency());
                transactions.insert(leg);
            }
            store.update(bill);
        });
    }
}
