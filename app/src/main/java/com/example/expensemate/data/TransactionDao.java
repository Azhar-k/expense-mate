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
    @Query("SELECT * FROM transactions ORDER BY date DESC")
    LiveData<List<Transaction>> getAllTransactions();

    @Query("SELECT * FROM transactions ORDER BY date DESC")
    List<Transaction> getAllTransactionsSync();

    @Query("SELECT * FROM transactions WHERE transactionType = 'DEBIT' AND linkedRecurringPaymentId IS NULL ORDER BY date DESC")
    LiveData<List<Transaction>> getDebitTransactions();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertTransaction(Transaction transaction);

    @Delete
    void deleteTransaction(Transaction transaction);

    @Query("SELECT * FROM transactions WHERE accountNumber = :accountNumber ORDER BY date DESC")
    LiveData<List<Transaction>> getTransactionsByAccount(String accountNumber);

    @Query("UPDATE transactions SET amount = :amount, description = :description, date = :date, " +
           "accountNumber = :accountNumber, accountType = :accountType, transactionType = :transactionType, " +
           "receiverName = :receiverName, smsBody = :smsBody, smsSender = :smsSender, category = :category, " +
           "linkedRecurringPaymentId = :linkedRecurringPaymentId WHERE id = :id")
    void updateTransaction(long id, double amount, String description, Date date, String accountNumber,
                         String accountType, String transactionType, String receiverName,
                         String smsBody, String smsSender, String category, Long linkedRecurringPaymentId);

    @Query("SELECT * FROM transactions WHERE linkedRecurringPaymentId = :paymentId")
    LiveData<List<Transaction>> getTransactionsByRecurringPaymentId(long paymentId);

    @Query("SELECT category, SUM(amount) as total FROM transactions " +
           "WHERE transactionType = 'DEBIT' AND linkedRecurringPaymentId IS NULL " +
           "AND strftime('%m', datetime(date/1000, 'unixepoch')) = :month " +
           "AND strftime('%Y', datetime(date/1000, 'unixepoch')) = :year " +
           "GROUP BY category ORDER BY total DESC")
    LiveData<List<CategorySum>> getCategorySumsByMonthYear(String month, String year);

    // Synchronous methods for getting totals
    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE transactionType = 'DEBIT' AND linkedRecurringPaymentId IS NULL " +
           "AND strftime('%m', datetime(date/1000, 'unixepoch')) = :month " +
           "AND strftime('%Y', datetime(date/1000, 'unixepoch')) = :year")
    Double getExpenseByMonthYearSync(String month, String year);

    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE transactionType = 'CREDIT' " +
           "AND strftime('%m', datetime(date/1000, 'unixepoch')) = :month " +
           "AND strftime('%Y', datetime(date/1000, 'unixepoch')) = :year")
    Double getIncomeByMonthYearSync(String month, String year);
} 