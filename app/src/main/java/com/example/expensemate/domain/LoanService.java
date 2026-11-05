package com.example.expensemate.domain;

import androidx.lifecycle.LiveData;

import com.example.expensemate.data.Loan;
import com.example.expensemate.data.LoanSummary;
import com.example.expensemate.data.Transaction;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Money lent to people, and the transactions that gave it out or brought it back. */
public interface LoanService {
    LiveData<List<LoanSummary>> loans();

    LiveData<List<Transaction>> linkedTransactions(long loanId);

    /** @return the new loan's id */
    CompletableFuture<Long> add(Loan loan);

    /** The debit that sent the money stays linked, so the loan shows where it came from. */
    CompletableFuture<Long> addFromTransaction(Loan loan, long transactionId);

    CompletableFuture<Void> update(Loan loan);

    /** The transactions stay in the history, just no longer linked. */
    CompletableFuture<Void> delete(Loan loan);

    /** A null loan unlinks the transaction. */
    CompletableFuture<Void> link(long transactionId, Long loanId);

    /** A return that never showed up as a transaction, such as cash. */
    CompletableFuture<Void> recordReturn(Loan loan, double amount);

    CompletableFuture<Void> markReturned(Loan loan);

    /** Keeps the closing date, which may belong to a return recorded by hand. */
    CompletableFuture<Void> reopen(Loan loan);
}
