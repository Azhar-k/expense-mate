package com.example.expensemate.domain;

import androidx.lifecycle.LiveData;

import com.example.expensemate.data.CategorySum;
import com.example.expensemate.data.SummaryTotals;
import com.example.expensemate.data.Transaction;

import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Transactions and transfers. Writes finish in the background; the future reports how they went. */
public interface TransactionService {
    LiveData<List<Transaction>> find(TransactionQuery query);

    LiveData<List<Transaction>> findForAccount(long accountId, Date from, Date to);

    LiveData<Transaction> get(long id);

    LiveData<List<Transaction>> transferLegs(String groupId);

    LiveData<List<Transaction>> transferLegsBetween(Date from, Date to);

    /** From inclusive, to exclusive; a null account means all accounts. */
    LiveData<SummaryTotals> summary(Date from, Date to, Long accountId);

    LiveData<List<CategorySum>> categoryTotals(String transactionType, Date from, Date to, Long accountId);

    LiveData<Integer> needsReviewCount();

    /** Fills in missing accounts, remembers the category for the counterparty, and marks a newly linked bill paid. */
    CompletableFuture<Void> add(Transaction transaction);

    /** Same rules as add; a category kept from before is not a new choice. */
    CompletableFuture<Void> update(Transaction edited);

    CompletableFuture<Void> chooseCategory(long id, String category);

    CompletableFuture<Void> setAccount(long id, long accountId);

    CompletableFuture<Void> setRealAccount(long id, long accountId);

    CompletableFuture<Void> setExcludedFromSummary(long id, boolean excluded);

    /** Deletes the row, or both sides if it is a transfer, and returns what went so it can be restored. */
    CompletableFuture<List<Transaction>> delete(long id);

    CompletableFuture<Void> restore(List<Transaction> removed);

    CompletableFuture<Void> addTransfer(Transaction debit, Transaction credit);

    CompletableFuture<Void> updateTransfer(Transaction debit, Transaction credit);
}
