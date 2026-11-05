package com.example.expensemate.domain.local;

import androidx.lifecycle.LiveData;

import com.example.expensemate.data.Account;
import com.example.expensemate.data.CategorySum;
import com.example.expensemate.data.SummaryTotals;
import com.example.expensemate.data.Transaction;
import com.example.expensemate.data.store.TransactionStore;
import com.example.expensemate.domain.TransactionQuery;
import com.example.expensemate.domain.TransactionService;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

final class LocalTransactionService implements TransactionService {
    private final TransactionStore store;
    private final TransactionRules rules;
    private final Writer writer;

    LocalTransactionService(TransactionStore store, TransactionRules rules, Writer writer) {
        this.store = store;
        this.rules = rules;
        this.writer = writer;
    }

    @Override
    public LiveData<List<Transaction>> find(TransactionQuery query) {
        return store.observe(query);
    }

    @Override
    public LiveData<List<Transaction>> findForAccount(long accountId, Date from, Date to) {
        return store.observeForAccount(accountId, from, to);
    }

    @Override
    public LiveData<Transaction> get(long id) {
        return store.observe(id);
    }

    @Override
    public LiveData<List<Transaction>> transferLegs(String groupId) {
        return store.observeTransferLegs(groupId);
    }

    @Override
    public LiveData<List<Transaction>> transferLegsBetween(Date from, Date to) {
        return store.observeTransferLegsBetween(from, to);
    }

    @Override
    public LiveData<SummaryTotals> summary(Date from, Date to, Long accountId) {
        return store.observeSummary(from, to, accountId);
    }

    @Override
    public LiveData<List<CategorySum>> categoryTotals(String transactionType, Date from, Date to, Long accountId) {
        return store.observeCategorySums(transactionType, from, to, accountId);
    }

    @Override
    public LiveData<Integer> needsReviewCount() {
        return store.observeNeedsReviewCount();
    }

    @Override
    public CompletableFuture<Void> add(Transaction transaction) {
        return writer.run(() -> {
            rules.fillDefaultAccounts(transaction);
            store.insert(transaction);
            rules.rememberCategory(transaction);
            rules.markLinkedBillPaid(transaction, null);
        });
    }

    @Override
    public CompletableFuture<Void> update(Transaction edited) {
        return writer.run(() -> {
            Transaction before = store.findById(edited.getId());
            store.update(edited);
            // Editing an old row's note is not a new choice, and must not undo a newer one.
            if (before == null || !sameChoice(before, edited)) {
                rules.rememberCategory(edited);
            }
            rules.markLinkedBillPaid(edited, before != null ? before.getLinkedRecurringPaymentId() : null);
        });
    }

    private static boolean sameChoice(Transaction before, Transaction after) {
        return !before.isNeedsReview()
                && Objects.equals(before.getCategory(), after.getCategory())
                && Objects.equals(before.getReceiverName(), after.getReceiverName())
                && Objects.equals(before.getTransactionType(), after.getTransactionType());
    }

    @Override
    public CompletableFuture<Void> chooseCategory(long id, String category) {
        return writer.run(() -> {
            Transaction row = store.findById(id);
            if (row == null) {
                return;
            }
            store.setCategory(id, category);
            Transaction chosen = row.copy();
            chosen.setCategory(category);
            rules.rememberCategory(chosen);
        });
    }

    @Override
    public CompletableFuture<Void> setAccount(long id, long accountId) {
        return writer.run(() -> {
            store.setAccount(id, accountId);
            // A row that had no virtual account has no real one either, so it gets the default real account now.
            Transaction row = store.findById(id);
            Long defaultReal = rules.defaultAccountId(Account.TYPE_REAL);
            if (row != null && row.getRealAccountId() == null && defaultReal != null) {
                store.setRealAccount(id, defaultReal);
            }
        });
    }

    @Override
    public CompletableFuture<Void> setRealAccount(long id, long accountId) {
        return writer.run(() -> store.setRealAccount(id, accountId));
    }

    @Override
    public CompletableFuture<Void> setExcludedFromSummary(long id, boolean excluded) {
        return writer.run(() -> store.setExcludedFromSummary(id, excluded));
    }

    @Override
    public CompletableFuture<List<Transaction>> delete(long id) {
        return writer.call(() -> {
            Transaction row = store.findById(id);
            if (row == null) {
                return Collections.emptyList();
            }
            if (row.isTransfer()) {
                List<Transaction> legs = store.findTransferLegs(row.getTransferGroupId());
                store.deleteTransferGroup(row.getTransferGroupId());
                return legs;
            }
            store.delete(row);
            return Collections.singletonList(row);
        });
    }

    @Override
    public CompletableFuture<Void> restore(List<Transaction> removed) {
        return writer.run(() -> {
            for (Transaction transaction : removed) {
                store.insert(transaction);
            }
        });
    }

    @Override
    public CompletableFuture<Void> addTransfer(Transaction debit, Transaction credit) {
        return writer.run(() -> {
            rules.fillDefaultAccounts(debit);
            rules.fillDefaultAccounts(credit);
            store.insert(debit);
            store.insert(credit);
        });
    }

    @Override
    public CompletableFuture<Void> updateTransfer(Transaction debit, Transaction credit) {
        return writer.run(() -> {
            store.update(debit);
            store.update(credit);
        });
    }
}
