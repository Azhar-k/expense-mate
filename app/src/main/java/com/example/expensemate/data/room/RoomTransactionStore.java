package com.example.expensemate.data.room;

import androidx.lifecycle.LiveData;

import com.example.expensemate.data.AccountBalance;
import com.example.expensemate.data.CategorySum;
import com.example.expensemate.data.SummaryTotals;
import com.example.expensemate.data.Transaction;
import com.example.expensemate.data.TransactionDao;
import com.example.expensemate.data.store.TransactionStore;
import com.example.expensemate.domain.TransactionQuery;

import java.util.Date;
import java.util.List;

final class RoomTransactionStore implements TransactionStore {
    private final TransactionDao dao;

    RoomTransactionStore(TransactionDao dao) {
        this.dao = dao;
    }

    @Override
    public void insert(Transaction transaction) {
        dao.insertTransaction(transaction);
    }

    @Override
    public void update(Transaction transaction) {
        dao.updateRow(transaction);
    }

    @Override
    public void delete(Transaction transaction) {
        dao.deleteTransaction(transaction);
    }

    @Override
    public void deleteTransferGroup(String groupId) {
        dao.deleteTransfer(groupId);
    }

    @Override
    public Transaction findById(long id) {
        return dao.getTransactionSync(id);
    }

    @Override
    public List<Transaction> findTransferLegs(String groupId) {
        return dao.getTransferLegsSync(groupId);
    }

    @Override
    public LiveData<Transaction> observe(long id) {
        return dao.getTransaction(id);
    }

    @Override
    public LiveData<List<Transaction>> observeTransferLegs(String groupId) {
        return dao.getTransferLegs(groupId);
    }

    @Override
    public LiveData<List<Transaction>> observeTransferLegsBetween(Date from, Date to) {
        return dao.getTransferLegsBetween(from, to);
    }

    @Override
    public LiveData<List<Transaction>> observe(TransactionQuery q) {
        return dao.getFilteredTransactions(new Date(q.fromMillis), new Date(q.toMillis), q.accountId, q.search,
                q.description, q.receiver, q.category, q.amount, q.transactionType, q.excluded, q.billId,
                q.transfers, q.needsReview);
    }

    @Override
    public LiveData<List<Transaction>> observeForAccount(long accountId, Date from, Date to) {
        return dao.getTransactionsForAccountDetailsScreen(from, to, accountId);
    }

    @Override
    public void setCategory(long id, String category) {
        dao.setCategory(id, category);
    }

    @Override
    public void setAccount(long id, long accountId) {
        dao.setAccount(id, accountId);
    }

    @Override
    public void setRealAccount(long id, long accountId) {
        dao.setRealAccount(id, accountId);
    }

    @Override
    public void setExcludedFromSummary(long id, boolean excluded) {
        dao.setExcludedFromSummary(id, excluded);
    }

    @Override
    public void moveAccount(long fromAccountId, long toAccountId) {
        dao.moveToAccount(fromAccountId, toAccountId);
    }

    @Override
    public void moveRealAccount(long fromAccountId, long toAccountId) {
        dao.moveToRealAccount(fromAccountId, toAccountId);
    }

    @Override
    public int countBySmsHash(String smsHash) {
        return dao.countTransactionsBySmsHash(smsHash);
    }

    @Override
    public int countByDedupKey(String dedupKey, long fromTime, long toTime) {
        return dao.countByDedupKey(dedupKey, fromTime, toTime);
    }

    @Override
    public int countByMatchKey(String matchKey, long fromTime, long toTime) {
        return dao.countByMatchKey(matchKey, fromTime, toTime);
    }

    @Override
    public int countByMatchKeyAndSource(String matchKey, String source, long fromTime, long toTime) {
        return dao.countByMatchKeyAndSource(matchKey, source, fromTime, toTime);
    }

    @Override
    public LiveData<SummaryTotals> observeSummary(Date from, Date to, Long accountId) {
        return dao.getSummaryTotals(from, to, accountId);
    }

    @Override
    public LiveData<List<CategorySum>> observeCategorySums(String transactionType, Date from, Date to,
            Long accountId) {
        return dao.getCategorySums(transactionType, from, to, accountId);
    }

    @Override
    public LiveData<Integer> observeNeedsReviewCount() {
        return dao.getNeedsReviewCount();
    }

    @Override
    public LiveData<List<AccountBalance>> observeAccountBalances() {
        return dao.getAccountBalances();
    }
}
