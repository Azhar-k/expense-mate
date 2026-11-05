package com.example.expensemate.domain.local;

import androidx.lifecycle.LiveData;

import com.example.expensemate.data.Loan;
import com.example.expensemate.data.LoanSummary;
import com.example.expensemate.data.Transaction;
import com.example.expensemate.data.store.LoanStore;
import com.example.expensemate.domain.LoanService;

import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;

final class LocalLoanService implements LoanService {
    private final LoanStore store;
    private final Writer writer;

    LocalLoanService(LoanStore store, Writer writer) {
        this.store = store;
        this.writer = writer;
    }

    @Override
    public LiveData<List<LoanSummary>> loans() {
        return store.observeSummaries();
    }

    @Override
    public LiveData<List<Transaction>> linkedTransactions(long loanId) {
        return store.observeLinkedTransactions(loanId);
    }

    @Override
    public CompletableFuture<Long> add(Loan loan) {
        return writer.call(() -> store.insert(loan));
    }

    @Override
    public CompletableFuture<Long> addFromTransaction(Loan loan, long transactionId) {
        return writer.call(() -> {
            long id = store.insert(loan);
            store.setLoan(transactionId, id);
            return id;
        });
    }

    @Override
    public CompletableFuture<Void> update(Loan loan) {
        return writer.run(() -> store.update(loan));
    }

    @Override
    public CompletableFuture<Void> delete(Loan loan) {
        return writer.run(() -> {
            store.unlinkAll(loan.getId());
            store.deleteById(loan.getId());
        });
    }

    @Override
    public CompletableFuture<Void> link(long transactionId, Long loanId) {
        return writer.run(() -> store.setLoan(transactionId, loanId));
    }

    @Override
    public CompletableFuture<Void> recordReturn(Loan loan, double amount) {
        loan.setReturnedManually(loan.getReturnedManually() + amount);
        loan.setClosedDate(new Date());
        return update(loan);
    }

    @Override
    public CompletableFuture<Void> markReturned(Loan loan) {
        loan.setClosedManually(true);
        loan.setClosedDate(new Date());
        return update(loan);
    }

    @Override
    public CompletableFuture<Void> reopen(Loan loan) {
        loan.setClosedManually(false);
        return update(loan);
    }
}
