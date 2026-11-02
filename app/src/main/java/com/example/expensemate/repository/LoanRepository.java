package com.example.expensemate.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import com.example.expensemate.data.AppDatabase;
import com.example.expensemate.data.Loan;
import com.example.expensemate.data.LoanDao;
import com.example.expensemate.data.LoanSummary;
import com.example.expensemate.data.Transaction;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LoanRepository {
    private final LoanDao loanDao;
    private final LiveData<List<LoanSummary>> loans;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    public LoanRepository(Application application) {
        loanDao = AppDatabase.getDatabase(application).loanDao();
        loans = loanDao.getLoanSummaries();
    }

    public LiveData<List<LoanSummary>> getLoans() {
        return loans;
    }

    public LiveData<List<Transaction>> getLinkedTransactions(long loanId) {
        return loanDao.getLinkedTransactions(loanId);
    }

    public void insert(Loan loan) {
        executorService.execute(() -> loanDao.insert(loan));
    }

    public void insertFromTransaction(Loan loan, long transactionId) {
        executorService.execute(() -> loanDao.insertFromTransaction(loan, transactionId));
    }

    public void update(Loan loan) {
        executorService.execute(() -> loanDao.update(loan));
    }

    public void delete(Loan loan) {
        executorService.execute(() -> loanDao.delete(loan));
    }

    public void setLoan(long transactionId, Long loanId) {
        executorService.execute(() -> loanDao.setLoan(transactionId, loanId));
    }
}
