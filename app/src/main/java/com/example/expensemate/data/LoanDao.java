package com.example.expensemate.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface LoanDao {
    // Only credits count as returns; the debit that lent the money is linked too, as where it came from
    String SUMMARY = "SELECT l.*, l.returnedManually + COALESCE((SELECT SUM(t.amount) FROM transactions t "
            + "WHERE t.loanId = l.id AND t.transactionType = 'CREDIT'), 0) AS returned, "
            + "(SELECT MAX(t.date) FROM transactions t WHERE t.loanId = l.id AND t.transactionType = 'CREDIT') "
            + "AS lastReturnDate FROM loans l";

    @Query(SUMMARY + " ORDER BY l.date DESC, l.id DESC")
    LiveData<List<LoanSummary>> getLoanSummaries();

    @Query(SUMMARY + " ORDER BY l.date DESC, l.id DESC")
    List<LoanSummary> getLoanSummariesSync();

    @Query("SELECT * FROM loans ORDER BY id")
    List<Loan> getAllLoansSync();

    @Query("SELECT * FROM transactions WHERE loanId = :loanId ORDER BY date")
    LiveData<List<Transaction>> getLinkedTransactions(long loanId);

    @Insert
    long insert(Loan loan);

    @Update
    void update(Loan loan);

    @Query("DELETE FROM loans WHERE id = :id")
    void deleteById(long id);

    @Query("UPDATE transactions SET loanId = :loanId WHERE id = :transactionId")
    void setLoan(long transactionId, Long loanId);

    @Query("UPDATE transactions SET loanId = NULL WHERE loanId = :loanId")
    void unlinkAll(long loanId);
}
