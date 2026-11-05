package com.example.expensemate.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.example.expensemate.AppServices;
import com.example.expensemate.data.Loan;
import com.example.expensemate.data.LoanSummary;
import com.example.expensemate.data.Transaction;
import com.example.expensemate.domain.LoanService;

import java.util.List;

/** Money lent to people, and the transactions that gave it out or brought it back. */
public class LoanViewModel extends AndroidViewModel {
    private static final String TAG = "LoanViewModel";
    private final LoanService loans;

    public LoanViewModel(@NonNull Application application) {
        super(application);
        loans = AppServices.get(application).loans();
    }

    public LiveData<List<LoanSummary>> getLoans() {
        return loans.loans();
    }

    public LiveData<List<Transaction>> getLinkedTransactions(long loanId) {
        return loans.linkedTransactions(loanId);
    }

    public void add(Loan loan) {
        Writes.log(TAG, "adding loan", loans.add(loan));
    }

    public void addFromTransaction(Loan loan, long transactionId) {
        Writes.log(TAG, "adding loan", loans.addFromTransaction(loan, transactionId));
    }

    public void update(Loan loan) {
        Writes.log(TAG, "updating loan", loans.update(loan));
    }

    public void delete(Loan loan) {
        Writes.log(TAG, "deleting loan", loans.delete(loan));
    }

    public void link(long transactionId, long loanId) {
        Writes.log(TAG, "linking loan", loans.link(transactionId, loanId));
    }

    public void unlink(long transactionId) {
        Writes.log(TAG, "unlinking loan", loans.link(transactionId, null));
    }

    public void recordReturn(Loan loan, double amount) {
        Writes.log(TAG, "recording return", loans.recordReturn(loan, amount));
    }

    public void markReturned(Loan loan) {
        Writes.log(TAG, "closing loan", loans.markReturned(loan));
    }

    public void reopen(Loan loan) {
        Writes.log(TAG, "reopening loan", loans.reopen(loan));
    }
}
