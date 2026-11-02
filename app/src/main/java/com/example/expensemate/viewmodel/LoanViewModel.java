package com.example.expensemate.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.example.expensemate.data.Loan;
import com.example.expensemate.data.LoanSummary;
import com.example.expensemate.data.Transaction;
import com.example.expensemate.repository.LoanRepository;

import java.util.Date;
import java.util.List;

/** Money lent to people, and the transactions that gave it out or brought it back. */
public class LoanViewModel extends AndroidViewModel {
    private final LoanRepository repository;

    public LoanViewModel(@NonNull Application application) {
        super(application);
        repository = new LoanRepository(application);
    }

    public LiveData<List<LoanSummary>> getLoans() {
        return repository.getLoans();
    }

    public LiveData<List<Transaction>> getLinkedTransactions(long loanId) {
        return repository.getLinkedTransactions(loanId);
    }

    public void add(Loan loan) {
        repository.insert(loan);
    }

    /** The debit that sent the money stays linked, so the loan shows where it came from. */
    public void addFromTransaction(Loan loan, long transactionId) {
        repository.insertFromTransaction(loan, transactionId);
    }

    public void update(Loan loan) {
        repository.update(loan);
    }

    public void delete(Loan loan) {
        repository.delete(loan);
    }

    public void link(long transactionId, long loanId) {
        repository.setLoan(transactionId, loanId);
    }

    public void unlink(long transactionId) {
        repository.setLoan(transactionId, null);
    }

    /** A return that never showed up as a transaction, such as cash. */
    public void recordReturn(Loan loan, double amount) {
        loan.setReturnedManually(loan.getReturnedManually() + amount);
        repository.update(loan);
    }

    public void markReturned(Loan loan) {
        loan.setClosedManually(true);
        loan.setClosedDate(new Date());
        repository.update(loan);
    }

    public void reopen(Loan loan) {
        loan.setClosedManually(false);
        loan.setClosedDate(null);
        repository.update(loan);
    }
}
