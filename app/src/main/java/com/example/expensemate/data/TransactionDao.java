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

        // Updates the fields a person can edit. Message keys and the transfer link stay as they are.
        @Query("UPDATE transactions SET amount = :amount, description = :description, date = :date, " +
                        "transactionType = :transactionType, " +
                        "receiverName = :receiverName, smsBody = :smsBody, smsSender = :smsSender, category = :category, "
                        +
                        "linkedRecurringPaymentId = :linkedRecurringPaymentId, isExcludedFromSummary = :isExcludedFromSummary, "
                        +
                        "accountId = :accountId, needsReview = :needsReview WHERE id = :id")
        void updateTransaction(long id, double amount, String description, Date date, String transactionType,
                        String receiverName,
                        String smsBody, String smsSender, String category, Long linkedRecurringPaymentId,
                        Long accountId, boolean isExcludedFromSummary, boolean needsReview);

        @Query("UPDATE transactions SET category = :category, needsReview = 0 WHERE id = :id")
        void setCategory(long id, String category);

        @Query("UPDATE transactions SET isExcludedFromSummary = :excluded WHERE id = :id")
        void setExcludedFromSummary(long id, boolean excluded);

        @Query("SELECT * FROM transactions WHERE id = :id")
        LiveData<Transaction> getTransaction(long id);

        /****************************************************************************************************/
        // Transfers: two rows that move money between accounts and are never income or spending

        @Query("SELECT * FROM transactions WHERE transferGroupId = :groupId")
        LiveData<List<Transaction>> getTransferLegs(String groupId);

        @Query("SELECT * FROM transactions WHERE transferGroupId = :groupId")
        List<Transaction> getTransferLegsSync(String groupId);

        // Both sides of a transfer share one date, so a date range finds both.
        @Query("SELECT * FROM transactions WHERE transferGroupId IS NOT NULL AND date BETWEEN :fromDate AND :toDate")
        LiveData<List<Transaction>> getTransferLegsBetween(Date fromDate, Date toDate);

        @androidx.room.Transaction
        default void insertTransfer(Transaction debit, Transaction credit) {
                insertTransaction(debit);
                insertTransaction(credit);
        }

        @androidx.room.Transaction
        default void updateTransfer(Transaction debit, Transaction credit) {
                updateRow(debit);
                updateRow(credit);
        }

        default void updateRow(Transaction row) {
                updateTransaction(row.getId(), row.getAmount(), row.getDescription(), row.getDate(),
                                row.getTransactionType(), row.getReceiverName(), row.getSmsBody(), row.getSmsSender(),
                                row.getCategory(), row.getLinkedRecurringPaymentId(), row.getAccountId(),
                                row.isExcludedFromSummary(), row.isNeedsReview());
        }

        @Query("DELETE FROM transactions WHERE transferGroupId = :groupId")
        void deleteTransfer(String groupId);

        /****************************************************************************************************/
        // Home screen: months are local-time ranges, so a payment just after midnight lands in the right month

        @Query("SELECT " +
                        "COALESCE(SUM(CASE WHEN transactionType = 'DEBIT' AND transferGroupId IS NULL AND isExcludedFromSummary = 0 THEN amount END), 0) AS expense, "
                        +
                        "COALESCE(SUM(CASE WHEN transactionType = 'CREDIT' AND transferGroupId IS NULL AND isExcludedFromSummary = 0 THEN amount END), 0) AS income, "
                        +
                        "COALESCE(SUM(CASE WHEN transactionType = 'DEBIT' AND transferGroupId IS NOT NULL AND isExcludedFromSummary = 0 THEN amount END), 0) AS movedOut, "
                        +
                        "COALESCE(SUM(CASE WHEN transactionType = 'CREDIT' AND transferGroupId IS NOT NULL AND isExcludedFromSummary = 0 THEN amount END), 0) AS movedIn "
                        +
                        "FROM transactions WHERE date >= :fromDate AND date < :toDate " +
                        "AND (:accountId IS NULL OR accountId = :accountId)")
        LiveData<SummaryTotals> getSummaryTotals(Date fromDate, Date toDate, Long accountId);

        @Query("SELECT category, SUM(amount) as total FROM transactions " +
                        "WHERE transactionType = :transactionType AND isExcludedFromSummary = 0 " +
                        "AND transferGroupId IS NULL " +
                        "AND date >= :fromDate AND date < :toDate " +
                        "AND (:accountId IS NULL OR accountId = :accountId) " +
                        "GROUP BY category ORDER BY total DESC")
        LiveData<List<CategorySum>> getCategorySums(String transactionType, Date fromDate, Date toDate, Long accountId);

        @Query("SELECT COUNT(*) FROM transactions WHERE needsReview = 1 AND isExcludedFromSummary = 0 " +
                        "AND transferGroupId IS NULL")
        LiveData<Integer> getNeedsReviewCount();

        /****************************************************************************************************/
        // Balances: transfers count here, because they really do move money between accounts

        @Query("SELECT COALESCE(SUM(CASE WHEN transactionType = 'CREDIT' THEN amount WHEN transactionType = 'DEBIT' THEN -amount END), 0) "
                        + "FROM transactions WHERE isExcludedFromSummary = 0")
        LiveData<Double> getNetBalance();

        @Query("SELECT accountId, COALESCE(SUM(CASE WHEN transactionType = 'CREDIT' THEN amount WHEN transactionType = 'DEBIT' THEN -amount END), 0) AS balance "
                        + "FROM transactions WHERE isExcludedFromSummary = 0 AND accountId IS NOT NULL GROUP BY accountId")
        LiveData<List<AccountBalance>> getAccountBalances();

        /****************************************************************************************************/
        // Account details screen

        @Query("SELECT * FROM transactions " +
                        "WHERE date BETWEEN :startDate AND :endDate " +
                        "AND accountId = :accountId " +
                        "ORDER BY date DESC")
        LiveData<List<Transaction>> getTransactionsForAccountDetailsScreen(Date startDate, Date endDate,
                        long accountId);

        /****************************************************************************************************/
        // Activity screen

        @Query("SELECT * FROM transactions " +
                        "WHERE date BETWEEN :fromDate AND :toDate " +
                        "AND (:accountId IS NULL OR accountId = :accountId) " +
                        "AND (:search IS NULL OR description LIKE '%' || :search || '%' " +
                        "OR receiverName LIKE '%' || :search || '%' " +
                        "OR CAST(amount AS TEXT) LIKE :search || '%') " +
                        "AND (:description IS NULL OR description LIKE '%' || :description || '%') " +
                        "AND (:receiverName IS NULL OR receiverName LIKE '%' || :receiverName || '%') " +
                        "AND (:category IS NULL OR category = :category) " +
                        "AND (:amount IS NULL OR amount = :amount) " +
                        "AND (:transactionType IS NULL OR transactionType = :transactionType) " +
                        "AND (:isExcludedFromSummary IS NULL OR isExcludedFromSummary = :isExcludedFromSummary) " +
                        "AND (:linkedRecurringPaymentId IS NULL OR linkedRecurringPaymentId = :linkedRecurringPaymentId) "
                        +
                        "AND (:transfers IS NULL OR (:transfers = 1 AND transferGroupId IS NOT NULL) " +
                        "OR (:transfers = 0 AND transferGroupId IS NULL)) " +
                        "AND (:needsReview IS NULL OR (needsReview = :needsReview AND isExcludedFromSummary = 0 " +
                        "AND transferGroupId IS NULL)) " +
                        "ORDER BY date DESC")
        LiveData<List<Transaction>> getFilteredTransactions(Date fromDate, Date toDate, Long accountId, String search,
                        String description, String receiverName, String category, Double amount,
                        String transactionType, Boolean isExcludedFromSummary, Long linkedRecurringPaymentId,
                        Boolean transfers, Boolean needsReview);

        @Query("SELECT * FROM transactions WHERE date BETWEEN :fromDate AND :toDate ORDER BY date DESC")
        List<Transaction> getTransactionsBetweenSync(Date fromDate, Date toDate);

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
