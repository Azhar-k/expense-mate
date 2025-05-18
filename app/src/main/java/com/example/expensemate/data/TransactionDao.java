package com.example.expensemate.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.Date;
import java.util.List;

@Dao
public interface TransactionDao {
    @Query("SELECT * FROM transactions " +
           "WHERE strftime('%m', datetime(date/1000, 'unixepoch')) = :month " +
           "AND strftime('%Y', datetime(date/1000, 'unixepoch')) = :year " +
           "ORDER BY date DESC")
    List<Transaction> getTransactionsByMonthYearSync(String month, String year);

    @Query("SELECT * FROM transactions " +
           "WHERE strftime('%m', datetime(date/1000, 'unixepoch')) = :month " +
           "AND strftime('%Y', datetime(date/1000, 'unixepoch')) = :year " +
           "AND (:accountId IS NULL OR accountId = :accountId) " +
           "ORDER BY date DESC")
    List<Transaction> getTransactionsByMonthYearAndAccountSync(String month, String year, Long accountId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertTransaction(Transaction transaction);

    @Delete
    void deleteTransaction(Transaction transaction);

    @Query("UPDATE transactions SET amount = :amount, description = :description, date = :date, " +
           "transactionType = :transactionType, " +
           "receiverName = :receiverName, smsBody = :smsBody, smsSender = :smsSender, category = :category, " +
           "linkedRecurringPaymentId = :linkedRecurringPaymentId, accountId = :accountId WHERE id = :id")
    void updateTransaction(long id, double amount, String description, Date date, String transactionType, String receiverName,
                         String smsBody, String smsSender, String category, Long linkedRecurringPaymentId, Long accountId);

    @Query("SELECT category, SUM(amount) as total FROM transactions " +
           "WHERE transactionType = 'DEBIT' AND linkedRecurringPaymentId IS NULL " +
           "AND strftime('%m', datetime(date/1000, 'unixepoch')) = :month " +
           "AND strftime('%Y', datetime(date/1000, 'unixepoch')) = :year " +
           "GROUP BY category ORDER BY total DESC")
    LiveData<List<CategorySum>> getCategorySumsByMonthYear(String month, String year);

    @Query("SELECT category, SUM(amount) as total FROM transactions " +
           "WHERE transactionType = 'DEBIT' AND linkedRecurringPaymentId IS NULL " +
           "AND strftime('%m', datetime(date/1000, 'unixepoch')) = :month " +
           "AND strftime('%Y', datetime(date/1000, 'unixepoch')) = :year " +
           "AND (:accountId IS NULL OR accountId = :accountId) " +
           "GROUP BY category ORDER BY total DESC")
    LiveData<List<CategorySum>> getCategorySumsByMonthYearAndAccount(String month, String year, Long accountId);

    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions " +
           "WHERE transactionType = 'DEBIT' AND linkedRecurringPaymentId IS NULL " +
           "AND strftime('%m', datetime(date/1000, 'unixepoch')) = :month " +
           "AND strftime('%Y', datetime(date/1000, 'unixepoch')) = :year")
    Double getExpenseByMonthYearSync(String month, String year);

    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions " +
           "WHERE transactionType = 'DEBIT' AND linkedRecurringPaymentId IS NULL " +
           "AND strftime('%m', datetime(date/1000, 'unixepoch')) = :month " +
           "AND strftime('%Y', datetime(date/1000, 'unixepoch')) = :year " +
           "AND (:accountId IS NULL OR accountId = :accountId)")
    Double getExpenseByMonthYearAndAccountSync(String month, String year, Long accountId);

    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions " +
           "WHERE transactionType = 'CREDIT' " +
           "AND strftime('%m', datetime(date/1000, 'unixepoch')) = :month " +
           "AND strftime('%Y', datetime(date/1000, 'unixepoch')) = :year")
    Double getIncomeByMonthYearSync(String month, String year);

    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions " +
           "WHERE transactionType = 'CREDIT' " +
           "AND strftime('%m', datetime(date/1000, 'unixepoch')) = :month " +
           "AND strftime('%Y', datetime(date/1000, 'unixepoch')) = :year " +
           "AND (:accountId IS NULL OR accountId = :accountId)")
    Double getIncomeByMonthYearAndAccountSync(String month, String year, Long accountId);

    @Query("SELECT COUNT(*) FROM transactions WHERE smsHash = :smsHash")
    int countTransactionsBySmsHash(String smsHash);

    @Query("SELECT * FROM transactions ORDER BY date DESC")
    List<Transaction> getAllTransactionsSync();
}