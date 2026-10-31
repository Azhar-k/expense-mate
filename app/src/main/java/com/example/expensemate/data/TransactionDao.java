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

        // Global Balance Queries
        @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE transactionType = 'CREDIT' AND isExcludedFromSummary = 0")
        double getTotalIncomeForAllAccounts();

        @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE transactionType = 'DEBIT' AND isExcludedFromSummary = 0")
        double getTotalExpenseForAllAccounts();

        @Query("UPDATE transactions SET amount = :amount, description = :description, date = :date, " +
                        "transactionType = :transactionType, " +
                        "receiverName = :receiverName, smsBody = :smsBody, smsSender = :smsSender, category = :category, "
                        +
                        "linkedRecurringPaymentId = :linkedRecurringPaymentId, isExcludedFromSummary = :isExcludedFromSummary, accountId = :accountId WHERE id = :id")
        void updateTransaction(long id, double amount, String description, Date date, String transactionType,
                        String receiverName,
                        String smsBody, String smsSender, String category, Long linkedRecurringPaymentId,
                        Long accountId, boolean isExcludedFromSummary);

        /****************************************************************************************************/
        // Summary screen

        // Summary screen expense
        @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions " +
                        "WHERE transactionType = 'DEBIT' AND isExcludedFromSummary = 0 " +
                        "AND strftime('%m', datetime(date/1000, 'unixepoch')) = :month " +
                        "AND strftime('%Y', datetime(date/1000, 'unixepoch')) = :year " +
                        "AND (:accountId IS NULL OR accountId = :accountId)")
        Double getExpenseForExpenseScreen(String month, String year, Long accountId);

        // Summary screen income
        @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions " +
                        "WHERE transactionType = 'CREDIT' AND isExcludedFromSummary = 0 " +
                        "AND strftime('%m', datetime(date/1000, 'unixepoch')) = :month " +
                        "AND strftime('%Y', datetime(date/1000, 'unixepoch')) = :year " +
                        "AND (:accountId IS NULL OR accountId = :accountId)")
        Double getIncomeForExpenseScreen(String month, String year, Long accountId);

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
                        "WHERE transactionType = 'CREDIT' AND isExcludedFromSummary = 0 " +
                        "AND strftime('%m', datetime(date/1000, 'unixepoch')) = :month " +
                        "AND strftime('%Y', datetime(date/1000, 'unixepoch')) = :year " +
                        "AND (:accountId IS NULL OR accountId = :accountId) " +
                        "GROUP BY category ORDER BY total DESC")
        LiveData<List<CategorySum>> getIncomeCategorySumForSummaryScreen(String month, String year, Long accountId);

        // Transactions shown for each category wise breakup in the summary screen
        @Query("SELECT * FROM transactions " +
                        "WHERE category = :category " +
                        "AND transactionType = :transactionType " +
                        "AND isExcludedFromSummary = 0 " +
                        "AND strftime('%m', datetime(date/1000, 'unixepoch')) = :month " +
                        "AND strftime('%Y', datetime(date/1000, 'unixepoch')) = :year " +
                        "AND (:accountId IS NULL OR accountId = :accountId) " +
                        "ORDER BY date DESC")
        LiveData<List<Transaction>> getTransactionsByCategoryForSummaryScreen(String category, String month,
                        String year, Long accountId, String transactionType);

        /****************************************************************************************************/
        // Account details screen

        // Account details screen income
        @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions " +
                        "WHERE transactionType = 'CREDIT' " +
                        "AND isExcludedFromSummary = 0 " +
                        "AND accountId = :accountId")
        double getTotalIncomeForAccountDetailsScreen(long accountId);

        // Account details screen expense
        @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions " +
                        "WHERE transactionType = 'DEBIT' " +
                        "AND isExcludedFromSummary = 0 " +
                        "AND accountId = :accountId")
        double getTotalExpenseForAccountDetailsScreen(long accountId);

        // Transactions for account details screen
        @Query("SELECT * FROM transactions " +
                        "WHERE date BETWEEN :startDate AND :endDate " +
                        "AND accountId = :accountId " +
                        "ORDER BY date DESC")
        LiveData<List<Transaction>> getTransactionsForAccountDetailsScreen(Date startDate, Date endDate,
                        long accountId);

        /****************************************************************************************************/
        // Transactions screen

        @Query("SELECT * FROM transactions " +
                        "WHERE date BETWEEN :fromDate AND :toDate " +
                        "AND (:accountId IS NULL OR accountId = :accountId) " +
                        "AND (:description IS NULL OR description LIKE '%' || :description || '%') " +
                        "AND (:receiverName IS NULL OR receiverName LIKE '%' || :receiverName || '%') " +
                        "AND (:category IS NULL OR category = :category) " +
                        "AND (:amount IS NULL OR amount = :amount) " +
                        "AND (:transactionType IS NULL OR transactionType = :transactionType) " +
                        "AND (:isExcludedFromSummary IS NULL OR isExcludedFromSummary = :isExcludedFromSummary) " +
                        "AND (:linkedRecurringPaymentId IS NULL OR linkedRecurringPaymentId = :linkedRecurringPaymentId) "
                        +
                        "ORDER BY date DESC")
        List<Transaction> getFilteredTransactions(Date fromDate, Date toDate, Long accountId,
                        String description, String receiverName, String category, Double amount,
                        String transactionType, Boolean isExcludedFromSummary, Long linkedRecurringPaymentId);

        /****************************************************************************************************/
        // Queries for purposes other than screen

        @Query("SELECT COUNT(*) FROM transactions WHERE smsHash = :smsHash")
        int countTransactionsBySmsHash(String smsHash);

        @Query("SELECT COUNT(*) FROM transactions WHERE dedupKey = :dedupKey " +
                        "AND messageTime BETWEEN :fromTime AND :toTime")
        int countByDedupKey(String dedupKey, long fromTime, long toTime);

        @Query("SELECT COUNT(*) FROM transactions WHERE matchKey = :matchKey " +
                        "AND messageTime BETWEEN :fromTime AND :toTime")
        int countByMatchKey(String matchKey, long fromTime, long toTime);

        @Query("SELECT COUNT(*) FROM transactions WHERE matchKey = :matchKey AND source = :source " +
                        "AND messageTime BETWEEN :fromTime AND :toTime")
        int countByMatchKeyAndSource(String matchKey, String source, long fromTime, long toTime);

        /**
         * Inserts a message-derived transaction unless it is already recorded. The check and
         * the insert share one database transaction, so two entry paths cannot both pass.
         *
         * @return true if a row was inserted
         */
        @androidx.room.Transaction
        default boolean insertIfNew(Transaction transaction) {
                if (isDuplicate(transaction)) {
                        return false;
                }
                insertTransaction(transaction);
                return true;
        }

        default boolean isDuplicate(Transaction transaction) {
                long fromTime = transaction.getMessageTime() - TransactionKeys.DUPLICATE_WINDOW_MS;
                long toTime = transaction.getMessageTime() + TransactionKeys.DUPLICATE_WINDOW_MS;
                String matchKey = transaction.getMatchKey();

                // A Truecaller alert has no text to compare, so amount and merchant decide.
                if (TransactionKeys.SOURCE_TRUECALLER.equals(transaction.getSource())) {
                        return matchKey != null && countByMatchKey(matchKey, fromTime, toTime) > 0;
                }

                String dedupKey = transaction.getDedupKey();
                if (dedupKey != null && countByDedupKey(dedupKey, fromTime, toTime) > 0) {
                        return true;
                }
                // Keeps recognising older rows whose date was edited after they were saved.
                String smsHash = transaction.getSmsHash();
                if (smsHash != null && countTransactionsBySmsHash(smsHash) > 0) {
                        return true;
                }
                // Different full-text messages can be genuine repeats, so only a Truecaller
                // alert for the same payment counts here.
                return matchKey != null && countByMatchKeyAndSource(matchKey,
                                TransactionKeys.SOURCE_TRUECALLER, fromTime, toTime) > 0;
        }

        @Query("SELECT * FROM transactions ORDER BY date DESC")
        List<Transaction> getAllTransactionsSyncOrderByDateAsc();
}