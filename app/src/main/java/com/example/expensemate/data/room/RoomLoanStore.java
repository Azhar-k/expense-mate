package com.example.expensemate.data.room;

import androidx.lifecycle.LiveData;

import com.example.expensemate.data.Loan;
import com.example.expensemate.data.LoanDao;
import com.example.expensemate.data.LoanSummary;
import com.example.expensemate.data.Transaction;
import com.example.expensemate.data.store.LoanStore;

import java.util.List;

final class RoomLoanStore implements LoanStore {
    private final LoanDao dao;

    RoomLoanStore(LoanDao dao) {
        this.dao = dao;
    }

    @Override
    public LiveData<List<LoanSummary>> observeSummaries() {
        return dao.getLoanSummaries();
    }

    @Override
    public LiveData<List<Transaction>> observeLinkedTransactions(long loanId) {
        return dao.getLinkedTransactions(loanId);
    }

    @Override
    public long insert(Loan loan) {
        return dao.insert(loan);
    }

    @Override
    public void update(Loan loan) {
        dao.update(loan);
    }

    @Override
    public void deleteById(long id) {
        dao.deleteById(id);
    }

    @Override
    public void setLoan(long transactionId, Long loanId) {
        dao.setLoan(transactionId, loanId);
    }

    @Override
    public void unlinkAll(long loanId) {
        dao.unlinkAll(loanId);
    }
}
