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
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertTransaction(Transaction transaction);

    @Delete
    void deleteTransaction(Transaction transaction);

    @Query("UPDATE transactions SET amount = :amount, description = :description, date = :date, " +
            "transactionType = :transactionType, " +
            "receiverName = :receiverName, smsBody = :smsBody, smsSender = :smsSender, category = :category, " +
            "linkedRecurringPaymentId = :linkedRecurringPaymentId, isExcludedFromSummary = :isExcludedFromSummary, accountId = :accountId WHERE id = :id")
    void updateTransaction(long id, double amount, String description, Date date, String transactionType, String receiverName,
                           String smsBody, String smsSender, String category, Long linkedRecurringPaymentId, Long accountId, boolean isExcludedFromSummary);

    /****************************************************************************************************/
    //Home screen

    //Home screen expense
    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions " +
            "WHERE transactionType = 'DEBIT' AND isExcludedFromSummary = 0 " +
            "AND strftime('%m', datetime(date/1000, 'unixepoch')) = :month " +
            "AND strftime('%Y', datetime(date/1000, 'unixepoch')) = :year " +
            "AND (:accountId IS NULL OR accountId = :accountId)")
    Double getExpenseForExpenseScreen(String month, String year, Long accountId);

    //Home screen income
    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions " +
            "WHERE transactionType = 'CREDIT'" +
            "AND strftime('%m', datetime(date/1000, 'unixepoch')) = :month " +
            "AND strftime('%Y', datetime(date/1000, 'unixepoch')) = :year " +
            "AND (:accountId IS NULL OR accountId = :accountId)")
    Double getIncomeForExpenseScreen(String month, String year, Long accountId);

    /****************************************************************************************************/
    //Summary screen

    // Category sum for expense section of summary screen
    @Query("SELECT category, SUM(amount) as total FROM transactions " +
           "WHERE transactionType = 'DEBIT' AND isExcludedFromSummary = 0 " +
           "AND strftime('%m', datetime(date/1000, 'unixepoch')) = :month " +
           "AND strftime('%Y', datetime(date/1000, 'unixepoch')) = :year " +
           "AND (:accountId IS NULL OR accountId = :accountId) " +
           "GROUP BY category ORDER BY total DESC")
    LiveData<List<CategorySum>> getExpenseCategorySumForSummaryScreen(String month, String year, Long accountId);

    // Category sum for income section of summary screen
    @Query("SELECT category, SUM(amount) as total FROM transactions " +
           "WHERE transactionType = 'CREDIT' " +
           "AND strftime('%m', datetime(date/1000, 'unixepoch')) = :month " +
           "AND strftime('%Y', datetime(date/1000, 'unixepoch')) = :year " +
           "AND (:accountId IS NULL OR accountId = :accountId) " +
           "GROUP BY category ORDER BY total DESC")
    LiveData<List<CategorySum>> getIncomeCategorySumForSummaryScreen(String month, String year, Long accountId);

    //Transactions shown for each category wise breakup in the summary screen
    @Query("SELECT * FROM transactions " +
            "WHERE category = :category " +
            "AND transactionType = :transactionType " +
            "AND isExcludedFromSummary = 0 " +
            "AND strftime('%m', datetime(date/1000, 'unixepoch')) = :month " +
            "AND strftime('%Y', datetime(date/1000, 'unixepoch')) = :year " +
            "AND (:accountId IS NULL OR accountId = :accountId) " +
            "ORDER BY date DESC")
    LiveData<List<Transaction>> getTransactionsByCategoryForSummaryScreen(String category, String month, String year, Long accountId, String transactionType);

    /****************************************************************************************************/
    //Account details screen

    //Account details screen income
    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions " +
           "WHERE transactionType = 'CREDIT' " +
            "AND isExcludedFromSummary = 0 " +
           "AND accountId = :accountId")
    double getTotalIncomeForAccountDetailsScreen(long accountId);

    //Account details screen expense
    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions " +
           "WHERE transactionType = 'DEBIT' " +
            "AND isExcludedFromSummary = 0 " +
           "AND accountId = :accountId")
    double getTotalExpenseForAccountDetailsScreen(long accountId);

    //Transactions for account details screen
    @Query("SELECT * FROM transactions " +
           "WHERE date BETWEEN :startDate AND :endDate " +
           "AND accountId = :accountId " +
           "ORDER BY date DESC")
    LiveData<List<Transaction>> getTransactionsForAccountDetailsScreen(Date startDate, Date endDate, long accountId);

    /****************************************************************************************************/
    //Transactions screen

    //Transactions for transaction screen with account filter
    @Query("SELECT * FROM transactions " +
            "WHERE strftime('%m', datetime(date/1000, 'unixepoch')) = :month " +
            "AND strftime('%Y', datetime(date/1000, 'unixepoch')) = :year " +
            "AND accountId = :accountId " +
            "ORDER BY date DESC")
    List<Transaction> getTransactionsByAccountForTransactionScreen(String month, String year, Long accountId);
    // Transactions for transaction screen without account filter
    @Query("SELECT * FROM transactions " +
            "WHERE strftime('%m', datetime(date/1000, 'unixepoch')) = :month " +
            "AND strftime('%Y', datetime(date/1000, 'unixepoch')) = :year " +
            "ORDER BY date DESC")
    List<Transaction> getTransactionsForTransactionScreen(String month, String year);

    /****************************************************************************************************/
    // Queries for purposes other than screen

    @Query("SELECT COUNT(*) FROM transactions WHERE smsHash = :smsHash")
    int countTransactionsBySmsHash(String smsHash);

    @Query("SELECT * FROM transactions ORDER BY date DESC")
    List<Transaction> getAllTransactionsSyncOrderByDateAsc();
}