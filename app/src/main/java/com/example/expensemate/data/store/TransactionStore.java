package com.example.expensemate.data.store;

import androidx.lifecycle.LiveData;

import com.example.expensemate.data.AccountBalance;
import com.example.expensemate.data.CategorySum;
import com.example.expensemate.data.PastTransaction;
import com.example.expensemate.data.SummaryTotals;
import com.example.expensemate.data.Transaction;
import com.example.expensemate.domain.TransactionQuery;

import java.util.Date;
import java.util.List;

/**
 * Reads and writes transaction rows. No rules live here: deciding what a row should hold
 * is the service's job. The totals are query contracts any database must keep:
 * income and spending leave out transfers, and excluded rows count nowhere.
 */
public interface TransactionStore {
    /** Replaces a row with the same id, so undo can put a deleted row back. */
    void insert(Transaction transaction);

    /** Writes the fields a person can edit; message keys and the transfer link stay. */
    void update(Transaction transaction);

    void delete(Transaction transaction);

    void deleteTransferGroup(String groupId);

    Transaction findById(long id);

    List<Transaction> findTransferLegs(String groupId);

    LiveData<Transaction> observe(long id);

    LiveData<List<Transaction>> observeTransferLegs(String groupId);

    /** Both sides of a transfer share one date, so a range finds both. */
    LiveData<List<Transaction>> observeTransferLegsBetween(Date from, Date to);

    LiveData<List<Transaction>> observe(TransactionQuery query);

    LiveData<List<Transaction>> observeForAccount(long accountId, Date from, Date to);

    void setCategory(long id, String category);

    void setAccount(long id, long accountId);

    void setRealAccount(long id, long accountId);

    void setExcludedFromSummary(long id, boolean excluded);

    void moveAccount(long fromAccountId, long toAccountId);

    void moveRealAccount(long fromAccountId, long toAccountId);

    /** The latest rows that are not transfers, newest first. */
    List<PastTransaction> findHistory(int limit);

    int countBySmsHash(String smsHash);

    int countByDedupKey(String dedupKey, long fromTime, long toTime);

    int countByMatchKey(String matchKey, long fromTime, long toTime);

    int countByMatchKeyAndSource(String matchKey, String source, long fromTime, long toTime);

    /** Months are local-time ranges, from inclusive and to exclusive. */
    LiveData<SummaryTotals> observeSummary(Date from, Date to, Long accountId);

    LiveData<List<CategorySum>> observeCategorySums(String transactionType, Date from, Date to, Long accountId);

    LiveData<Integer> observeNeedsReviewCount();

    /** Transfers count here, because they really do move money between accounts. */
    LiveData<List<AccountBalance>> observeAccountBalances();
}
