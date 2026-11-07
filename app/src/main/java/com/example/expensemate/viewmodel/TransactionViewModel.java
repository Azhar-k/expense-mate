package com.example.expensemate.viewmodel;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import com.example.expensemate.AppServices;
import com.example.expensemate.data.AccountBalance;
import com.example.expensemate.data.CategorySum;
import com.example.expensemate.data.SummaryTotals;
import com.example.expensemate.data.Transaction;
import com.example.expensemate.domain.AccountService;
import com.example.expensemate.domain.CaptureService;
import com.example.expensemate.domain.Services;
import com.example.expensemate.domain.TransactionService;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

public class TransactionViewModel extends AndroidViewModel {
    private static final String TAG = "TransactionViewModel";
    private final TransactionService transactions;
    private final AccountService accounts;
    private final CaptureService capture;
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
        Services services = AppServices.get(application);
        transactions = services.transactions();
        accounts = services.accounts();
        capture = services.capture();

        activityRows = Transformations.switchMap(filter, this::rowsFor);

        homePeriod = new MutableLiveData<>(periodOf(Calendar.getInstance(), null));
        homeTotals = Transformations.switchMap(homePeriod,
                p -> transactions.summary(p.from, p.to, p.accountId));
        homeExpenses = Transformations.switchMap(homePeriod,
                p -> transactions.categoryTotals("DEBIT", p.from, p.to, p.accountId));
        homeIncome = Transformations.switchMap(homePeriod,
                p -> transactions.categoryTotals("CREDIT", p.from, p.to, p.accountId));
    }

    private LiveData<List<ActivityRow>> rowsFor(TransactionFilter f) {
        return combine(transactions.find(f.toQuery()),
                transactions.transferLegsBetween(new Date(f.fromMillis), new Date(f.toMillis)));
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

    /** For scans started from a screen; it blocks, so call it off the main thread. */
    public CaptureService getCapture() {
        return capture;
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
        return transactions.needsReviewCount();
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

    public LiveData<List<AccountBalance>> getAccountBalances() {
        return accounts.balances();
    }

    public LiveData<List<ActivityRow>> getRowsForAccount(long accountId, long fromMillis, long toMillis) {
        Date from = new Date(fromMillis);
        Date to = new Date(toMillis);
        return combine(transactions.findForAccount(accountId, from, to), transactions.transferLegsBetween(from, to));
    }

    /****************************************************************************************************/
    // Single transactions

    public LiveData<Transaction> getTransaction(long id) {
        return transactions.get(id);
    }

    public LiveData<List<Transaction>> getTransferLegs(String groupId) {
        return transactions.transferLegs(groupId);
    }

    public void insertTransaction(Transaction transaction) {
        Writes.log(TAG, "inserting transaction", transactions.add(transaction));
    }

    public void updateTransaction(Transaction edited) {
        Writes.log(TAG, "updating transaction", transactions.update(edited));
    }

    public void setCategory(Transaction transaction, String category) {
        Writes.log(TAG, "choosing category", transactions.chooseCategory(transaction.getId(), category));
    }

    public void setAccount(long id, long accountId) {
        Writes.log(TAG, "moving transaction", transactions.setAccount(id, accountId));
    }

    public void setRealAccount(long id, long accountId) {
        Writes.log(TAG, "moving transaction", transactions.setRealAccount(id, accountId));
    }

    public void setExcludedFromSummary(long id, boolean excluded) {
        Writes.log(TAG, "excluding transaction", transactions.setExcludedFromSummary(id, excluded));
    }

    /** Deletes the row, or both sides if it is a transfer, and reports what went so it can be restored. */
    public void delete(Transaction transaction, Consumer<List<Transaction>> onDeleted) {
        Writes.log(TAG, "deleting transaction", transactions.delete(transaction.getId())
                .thenAccept(removed -> mainThread.post(() -> onDeleted.accept(removed))));
    }

    /** Reports the bill the payment most likely pays, or null, on the main thread. */
    public void suggestBill(Transaction transaction, Consumer<Long> onSuggested) {
        Writes.log(TAG, "suggesting bill", transactions.suggestBill(transaction)
                .thenAccept(billId -> mainThread.post(() -> onSuggested.accept(billId))));
    }

    public void restore(List<Transaction> removed) {
        Writes.log(TAG, "restoring transaction", transactions.restore(removed));
    }

    public void insertTransfer(Transaction debit, Transaction credit) {
        Writes.log(TAG, "saving transfer", transactions.addTransfer(debit, credit));
    }

    public void updateTransfer(Transaction debit, Transaction credit) {
        Writes.log(TAG, "updating transfer", transactions.updateTransfer(debit, credit));
    }
}
