package com.example.expensemate.viewmodel;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import com.example.expensemate.data.Account;
import com.example.expensemate.data.AccountBalance;
import com.example.expensemate.data.AppDatabase;
import com.example.expensemate.data.CategorySum;
import com.example.expensemate.data.SummaryTotals;
import com.example.expensemate.data.Transaction;
import com.example.expensemate.data.TransactionDao;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class TransactionViewModel extends AndroidViewModel {
    private static final String TAG = "TransactionViewModel";
    private final TransactionDao transactionDao;
    private final ExecutorService executorService;
    private final AccountViewModel accountViewModel;
    private final Handler mainThread = new Handler(Looper.getMainLooper());

    private final MutableLiveData<TransactionFilter> filter = new MutableLiveData<>(TransactionFilter.lastDays(30));
    private final LiveData<List<ActivityRow>> activityRows;

    private final MutableLiveData<HomePeriod> homePeriod;
    private final LiveData<SummaryTotals> homeTotals;
    private final LiveData<List<CategorySum>> homeExpenses;
    private final LiveData<List<CategorySum>> homeIncome;
    private boolean homeShowingExpenses = true;
    private boolean homeAccountChosen = false;

    /** The month and account the Home screen is showing; a null account means all accounts. */
    private static final class HomePeriod {
        final Date from;
        final Date to;
        final Long accountId;

        HomePeriod(Date from, Date to, Long accountId) {
            this.from = from;
            this.to = to;
            this.accountId = accountId;
        }
    }

    public TransactionViewModel(Application application) {
        super(application);
        Log.d(TAG, "Initializing TransactionViewModel");
        transactionDao = AppDatabase.getDatabase(application).transactionDao();
        executorService = Executors.newSingleThreadExecutor();
        accountViewModel = new AccountViewModel(application);

        activityRows = Transformations.switchMap(filter, this::rowsFor);

        homePeriod = new MutableLiveData<>(periodOf(Calendar.getInstance(), null));
        homeTotals = Transformations.switchMap(homePeriod,
                p -> transactionDao.getSummaryTotals(p.from, p.to, p.accountId));
        homeExpenses = Transformations.switchMap(homePeriod,
                p -> transactionDao.getCategorySums("DEBIT", p.from, p.to, p.accountId));
        homeIncome = Transformations.switchMap(homePeriod,
                p -> transactionDao.getCategorySums("CREDIT", p.from, p.to, p.accountId));
    }

    private LiveData<List<ActivityRow>> rowsFor(TransactionFilter f) {
        Date from = new Date(f.fromMillis);
        Date to = new Date(f.toMillis);
        return combine(
                transactionDao.getFilteredTransactions(from, to, f.accountId, f.search, f.description, f.receiver,
                        f.category, f.amount, f.transactionType, f.excluded, f.billId, f.transfers, f.needsReview),
                transactionDao.getTransferLegsBetween(from, to));
    }

    private static LiveData<List<ActivityRow>> combine(LiveData<List<Transaction>> transactions,
            LiveData<List<Transaction>> legs) {
        MediatorLiveData<List<ActivityRow>> rows = new MediatorLiveData<>();
        Runnable update = () -> {
            if (transactions.getValue() != null && legs.getValue() != null) {
                rows.setValue(ActivityRow.build(transactions.getValue(), legs.getValue(), Calendar.getInstance()));
            }
        };
        rows.addSource(transactions, value -> update.run());
        rows.addSource(legs, value -> update.run());
        return rows;
    }

    private static HomePeriod periodOf(Calendar month, Long accountId) {
        Calendar start = (Calendar) month.clone();
        start.set(Calendar.DAY_OF_MONTH, 1);
        TransactionFilter.startOfDay(start);
        Calendar end = (Calendar) start.clone();
        end.add(Calendar.MONTH, 1);
        return new HomePeriod(start.getTime(), end.getTime(), accountId);
    }

    /****************************************************************************************************/
    // Home screen

    public LiveData<SummaryTotals> getHomeTotals() {
        return homeTotals;
    }

    public LiveData<List<CategorySum>> getHomeExpenses() {
        return homeExpenses;
    }

    public LiveData<List<CategorySum>> getHomeIncome() {
        return homeIncome;
    }

    public boolean isHomeShowingExpenses() {
        return homeShowingExpenses;
    }

    public void setHomeShowingExpenses(boolean showingExpenses) {
        homeShowingExpenses = showingExpenses;
    }

    public Long getHomeAccountId() {
        return homePeriod.getValue().accountId;
    }

    public void setHomeAccount(Long accountId) {
        homeAccountChosen = true;
        showHomeAccount(accountId);
    }

    /** Home opens on the default account, until the person picks one themselves. */
    public void applyDefaultHomeAccount(long accountId) {
        if (!homeAccountChosen) {
            showHomeAccount(accountId);
        }
    }

    private void showHomeAccount(Long accountId) {
        HomePeriod current = homePeriod.getValue();
        homePeriod.setValue(new HomePeriod(current.from, current.to, accountId));
    }

    public Calendar getHomeMonth() {
        Calendar month = Calendar.getInstance();
        month.setTime(homePeriod.getValue().from);
        return month;
    }

    public void moveHomeMonth(int months) {
        Calendar month = getHomeMonth();
        month.add(Calendar.MONTH, months);
        homePeriod.setValue(periodOf(month, getHomeAccountId()));
    }

    public LiveData<Integer> getNeedsReviewCount() {
        return transactionDao.getNeedsReviewCount();
    }

    /** Points the Activity list at one category in the month Home is showing. */
    public void showHomeCategory(String category, boolean expenses) {
        HomePeriod period = homePeriod.getValue();
        TransactionFilter f = TransactionFilter.lastDays(30);
        f.fromMillis = period.from.getTime();
        f.toMillis = period.to.getTime() - 1;
        f.rangeLabel = new java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault()).format(period.from);
        f.accountId = period.accountId;
        f.category = category;
        f.transactionType = expenses ? "DEBIT" : "CREDIT";
        f.transfers = false;
        filter.setValue(f);
    }

    public void showNeedsReview() {
        TransactionFilter f = TransactionFilter.lastDays(30);
        f.setAllTime();
        f.needsReview = true;
        filter.setValue(f);
    }

    /****************************************************************************************************/
    // Activity screen

    public LiveData<TransactionFilter> getFilter() {
        return filter;
    }

    public void setFilter(TransactionFilter newFilter) {
        filter.setValue(newFilter);
    }

    public void clearFilter() {
        filter.setValue(TransactionFilter.lastDays(30));
    }

    public LiveData<List<ActivityRow>> getActivityRows() {
        return activityRows;
    }

    /****************************************************************************************************/
    // Accounts

    public LiveData<Double> getNetBalance() {
        return transactionDao.getNetBalance();
    }

    public LiveData<List<AccountBalance>> getAccountBalances() {
        return transactionDao.getAccountBalances();
    }

    public LiveData<List<ActivityRow>> getRowsForAccount(long accountId, long fromMillis, long toMillis) {
        Date from = new Date(fromMillis);
        Date to = new Date(toMillis);
        return combine(transactionDao.getTransactionsForAccountDetailsScreen(from, to, accountId),
                transactionDao.getTransferLegsBetween(from, to));
    }

    /****************************************************************************************************/
    // Single transactions

    public LiveData<Transaction> getTransaction(long id) {
        return transactionDao.getTransaction(id);
    }

    public LiveData<List<Transaction>> getTransferLegs(String groupId) {
        return transactionDao.getTransferLegs(groupId);
    }

    public void insertTransaction(Transaction transaction) {
        executorService.execute(() -> {
            try {
                // If no account is set, use the default account
                if (transaction.getAccountId() == null) {
                    Account defaultAccount = accountViewModel.getDefaultAccountSync();
                    if (defaultAccount != null) {
                        transaction.setAccountId(defaultAccount.getId());
                    }
                }
                transactionDao.insertTransaction(transaction);
            } catch (Exception e) {
                Log.e(TAG, "Error inserting transaction", e);
            }
        });
    }

    public void updateTransaction(Transaction transaction) {
        executorService.execute(() -> {
            try {
                transactionDao.updateRow(transaction);
            } catch (Exception e) {
                Log.e(TAG, "Error updating transaction", e);
            }
        });
    }

    public void setCategory(long id, String category) {
        executorService.execute(() -> transactionDao.setCategory(id, category));
    }

    public void setExcludedFromSummary(long id, boolean excluded) {
        executorService.execute(() -> transactionDao.setExcludedFromSummary(id, excluded));
    }

    /** Deletes the row, or both sides if it is a transfer, and reports what went so it can be restored. */
    public void delete(Transaction transaction, Consumer<List<Transaction>> onDeleted) {
        executorService.execute(() -> {
            try {
                List<Transaction> removed;
                if (transaction.isTransfer()) {
                    removed = transactionDao.getTransferLegsSync(transaction.getTransferGroupId());
                    transactionDao.deleteTransfer(transaction.getTransferGroupId());
                } else {
                    removed = java.util.Collections.singletonList(transaction);
                    transactionDao.deleteTransaction(transaction);
                }
                mainThread.post(() -> onDeleted.accept(removed));
            } catch (Exception e) {
                Log.e(TAG, "Error deleting transaction", e);
            }
        });
    }

    public void restore(List<Transaction> transactions) {
        executorService.execute(() -> {
            for (Transaction transaction : transactions) {
                transactionDao.insertTransaction(transaction);
            }
        });
    }

    public void insertTransfer(Transaction debit, Transaction credit) {
        executorService.execute(() -> {
            try {
                transactionDao.insertTransfer(debit, credit);
            } catch (Exception e) {
                Log.e(TAG, "Error saving transfer", e);
            }
        });
    }

    public void updateTransfer(Transaction debit, Transaction credit) {
        executorService.execute(() -> {
            try {
                transactionDao.updateTransfer(debit, credit);
            } catch (Exception e) {
                Log.e(TAG, "Error updating transfer", e);
            }
        });
    }

    /**
     * Saves a message-derived transaction unless it is already recorded.
     * Runs on the calling thread so the result is known on return; do not call from the main thread.
     *
     * @return true if a row was inserted
     */
    public boolean insertIfNew(Transaction transaction) {
        if (transaction.getAccountId() == null) {
            Account defaultAccount = accountViewModel.getDefaultAccountSync();
            if (defaultAccount != null) {
                transaction.setAccountId(defaultAccount.getId());
            }
        }
        return transactionDao.insertIfNew(transaction);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executorService.shutdown();
    }
}
