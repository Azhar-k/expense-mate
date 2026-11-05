package com.example.expensemate.data.store;

import androidx.lifecycle.LiveData;

import com.example.expensemate.data.Loan;
import com.example.expensemate.data.LoanSummary;
import com.example.expensemate.data.Transaction;

import java.util.List;

public interface LoanStore {
    /** Only credits count as returns; the debit that lent the money is linked too. */
    LiveData<List<LoanSummary>> observeSummaries();

    LiveData<List<Transaction>> observeLinkedTransactions(long loanId);

    /** @return the new row's id */
    long insert(Loan loan);

    void update(Loan loan);

    void deleteById(long id);

    void setLoan(long transactionId, Long loanId);

    void unlinkAll(long loanId);
}
