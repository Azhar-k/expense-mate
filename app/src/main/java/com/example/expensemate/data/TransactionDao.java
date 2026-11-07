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
                        "accountId = :accountId, realAccountId = :realAccountId, needsReview = :needsReview WHERE id = :id")
        void updateTransaction(long id, double amount, String description, Date date, String transactionType,
                        String receiverName,
                        String smsBody, String smsSender, String category, Long linkedRecurringPaymentId,
                        Long accountId, Long realAccountId, boolean isExcludedFromSummary, boolean needsReview);

        @Query("UPDATE transactions SET category = :category, needsReview = 0 WHERE id = :id")
        void setCategory(long id, String category);

        @Query("UPDATE transactions SET accountId = :accountId WHERE id = :id")
        void setAccount(long id, long accountId);

        @Query("UPDATE transactions SET realAccountId = :accountId WHERE id = :id")
        void setRealAccount(long id, long accountId);

        @Query("UPDATE transactions SET accountId = :toAccountId WHERE accountId = :fromAccountId")
        void moveToAccount(long fromAccountId, long toAccountId);

        @Query("UPDATE transactions SET realAccountId = :toAccountId WHERE realAccountId = :fromAccountId")
        void moveToRealAccount(long fromAccountId, long toAccountId);

        @Query("UPDATE transactions SET isExcludedFromSummary = :excluded WHERE id = :id")
        void setExcludedFromSummary(long id, boolean excluded);

        @Query("SELECT * FROM transactions WHERE id = :id")
        LiveData<Transaction> getTransaction(long id);

        @Query("SELECT * FROM transactions WHERE id = :id")
        Transaction getTransactionSync(long id);

        /****************************************************************************************************/
        // Transfers: two rows that move money between accounts and are never income or spending

        @Query("SELECT * FROM transactions WHERE transferGroupId = :groupId")
        LiveData<List<Transaction>> getTransferLegs(String groupId);

        @Query("SELECT * FROM transactions WHERE transferGroupId = :groupId")
        List<Transaction> getTransferLegsSync(String groupId);

        // Both sides of a transfer share one date, so a date range finds both.
        @Query("SELECT * FROM transactions WHERE transferGroupId IS NOT NULL AND date BETWEEN :fromDate AND :toDate")
        LiveData<List<Transaction>> getTransferLegsBetween(Date fromDate, Date toDate);

        default void updateRow(Transaction row) {
                updateTransaction(row.getId(), row.getAmount(), row.getDescription(), row.getDate(),
                                row.getTransactionType(), row.getReceiverName(), row.getSmsBody(), row.getSmsSender(),
                                row.getCategory(), row.getLinkedRecurringPaymentId(), row.getAccountId(), row.getRealAccountId(),
                                row.isExcludedFromSummary(), row.isNeedsReview());
        }

        @Query("DELETE FROM transactions WHERE transferGroupId = :groupId")
        void deleteTransfer(String groupId);

        /****************************************************************************************************/
        // Counterparty categories: the last category chosen for a sender or receiver goes on their next captured payment

        // Going through categories leaves out a mapping whose category was deleted or moved to the other type.
        @Query("SELECT c.name FROM counterparty_categories m JOIN categories c ON c.id = m.categoryId " +
                        "AND c.type = CASE m.transactionType WHEN 'CREDIT' THEN 'INCOME' ELSE 'EXPENSE' END " +
                        "WHERE m.counterparty = :counterparty AND m.transactionType = :transactionType LIMIT 1")
        String getCounterpartyCategory(String counterparty, String transactionType);

        @Query("DELETE FROM counterparty_categories WHERE counterparty = :counterparty " +
                        "AND transactionType = :transactionType")
        void deleteCounterpartyCategory(String counterparty, String transactionType);

        @Query("INSERT OR REPLACE INTO counterparty_categories (counterparty, transactionType, categoryId) " +
                        "SELECT :counterparty, :transactionType, id FROM categories WHERE name = :category " +
                        "AND type = CASE :transactionType WHEN 'CREDIT' THEN 'INCOME' ELSE 'EXPENSE' END LIMIT 1")
        void saveCounterpartyCategory(String counterparty, String transactionType, String category);

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
                        "AND (:accountId IS NULL OR accountId = :accountId OR realAccountId = :accountId)")
        LiveData<SummaryTotals> getSummaryTotals(Date fromDate, Date toDate, Long accountId);

        @Query("SELECT category, SUM(amount) as total FROM transactions " +
                        "WHERE transactionType = :transactionType AND isExcludedFromSummary = 0 " +
                        "AND transferGroupId IS NULL " +
                        "AND date >= :fromDate AND date < :toDate " +
                        "AND (:accountId IS NULL OR accountId = :accountId OR realAccountId = :accountId) " +
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

        // A row counts once for its virtual account and once for its real account; their ids never overlap.
        @Query("SELECT accountId, COALESCE(SUM(CASE WHEN transactionType = 'CREDIT' THEN amount WHEN transactionType = 'DEBIT' THEN -amount END), 0) AS balance "
                        + "FROM (SELECT accountId, amount, transactionType FROM transactions WHERE isExcludedFromSummary = 0 "
                        + "UNION ALL SELECT realAccountId, amount, transactionType FROM transactions WHERE isExcludedFromSummary = 0) "
                        + "WHERE accountId IS NOT NULL GROUP BY accountId")
        LiveData<List<AccountBalance>> getAccountBalances();

        /****************************************************************************************************/
        // Account details screen

        @Query("SELECT * FROM transactions " +
                        "WHERE date BETWEEN :startDate AND :endDate " +
                        "AND (accountId = :accountId OR realAccountId = :accountId) " +
                        "ORDER BY date DESC")
        LiveData<List<Transaction>> getTransactionsForAccountDetailsScreen(Date startDate, Date endDate,
                        long accountId);

        /****************************************************************************************************/
        // Activity screen

        @Query("SELECT * FROM transactions " +
                        "WHERE date BETWEEN :fromDate AND :toDate " +
                        "AND (:accountId IS NULL OR accountId = :accountId OR realAccountId = :accountId) " +
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

        @Query("SELECT * FROM transactions ORDER BY date DESC")
        List<Transaction> getAllTransactionsSyncOrderByDateAsc();

        // Transfers are left out: they say nothing about where a payment goes or what it was for.
        @Query("SELECT id, receiverName, smsSender, transactionType, category, needsReview, accountId, " +
                        "realAccountId, linkedRecurringPaymentId FROM transactions WHERE transferGroupId IS NULL " +
                        "ORDER BY date DESC LIMIT :limit")
        List<PastTransaction> getHistory(int limit);
}
