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
import com.example.expensemate.data.CategorySum;
import com.example.expensemate.data.SummaryTotals;
import com.example.expensemate.data.Transaction;
import com.example.expensemate.data.Transfers;
import com.example.expensemate.domain.CaptureService;
import com.example.expensemate.domain.Services;
import com.example.expensemate.domain.TransactionService;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class TransactionViewModel extends AndroidViewModel {
    private static final String TAG = "TransactionViewModel";
    private final TransactionService transactions;
    private final CaptureService capture;
    private final Handler mainThread = new Handler(Looper.getMainLooper());

    private final MutableLiveData<TransactionFilter> filter = new MutableLiveData<>(TransactionFilter.lastDays(30));
    private final LiveData<List<ActivityRow>> activityRows;

    private final MutableLiveData<HomePeriod> homePeriod;
    private final LiveData<List<SummaryTotals>> homeTotals;
    private final LiveData<List<CategorySum>> homeExpenses;
    private final LiveData<List<CategorySum>> homeIncome;
    private boolean homeShowingExpenses = true;
    private boolean homeFilterChosen = false;
    private long lastAddedAt;

    /**
     * The month the Home screen is showing, and at most one of a fund or an account to narrow it to;
     * both null means everything. Their ids come from separate tables, so they are kept apart.
     */
    private static final class HomePeriod {
        final Date from;
        final Date to;
        final Long fundId;
        final Long accountId;
        final String currency; // The breakdown shows one currency at a time

        HomePeriod(Date from, Date to, Long fundId, Long accountId, String currency) {
            this.from = from;
            this.to = to;
            this.fundId = fundId;
            this.accountId = accountId;
            this.currency = currency;
        }
    }

    public TransactionViewModel(Application application) {
        super(application);
        Services services = AppServices.get(application);
        transactions = services.transactions();
        capture = services.capture();

        activityRows = Transformations.switchMap(filter, this::rowsFor);

        homePeriod = new MutableLiveData<>(periodOf(Calendar.getInstance(), null, null, null));
        homeTotals = Transformations.switchMap(homePeriod,
                p -> transactions.summary(p.from, p.to, p.fundId, p.accountId));
        homeExpenses = Transformations.switchMap(homePeriod,
                p -> transactions.categoryTotals("DEBIT", p.currency, p.from, p.to, p.fundId, p.accountId));
        homeIncome = Transformations.switchMap(homePeriod,
                p -> transactions.categoryTotals("CREDIT", p.currency, p.from, p.to, p.fundId, p.accountId));
    }

    private LiveData<List<ActivityRow>> rowsFor(TransactionFilter f) {
        // Moves and transfers are stored alike, so which one a pair is can only be read once its two sides meet
        Boolean moves = Boolean.TRUE.equals(f.transfers) ? f.moves : null;
        Predicate<ActivityRow> keep = moves == null ? null
                : moves ? ActivityRow::isMove : ActivityRow::isAccountTransfer;
        // Narrowed to one account or fund, a pair that changes nothing there is left out, as on its own page
        Predicate<ActivityRow> narrowed = f.accountId != null ? row -> !row.isMove()
                : f.fundId != null ? row -> !row.isTransfer() || !Transfers.staysInFund(row.debitLeg, row.creditLeg)
                : null;
        if (narrowed != null) {
            keep = keep == null ? narrowed : keep.and(narrowed);
        }
        return combine(transactions.find(f.toQuery()),
                transactions.transferLegsBetween(new Date(f.fromMillis), new Date(f.toMillis)), keep);
    }

    private static LiveData<List<ActivityRow>> combine(LiveData<List<Transaction>> transactions,
            LiveData<List<Transaction>> legs, Predicate<ActivityRow> keep) {
        MediatorLiveData<List<ActivityRow>> rows = new MediatorLiveData<>();
        Runnable update = () -> {
            if (transactions.getValue() != null && legs.getValue() != null) {
                List<ActivityRow> built = ActivityRow.build(transactions.getValue(), legs.getValue(), Calendar.getInstance());
                rows.setValue(keep == null ? built : ActivityRow.keep(built, keep));
            }
        };
        rows.addSource(transactions, value -> update.run());
        rows.addSource(legs, value -> update.run());
        return rows;
    }

    private static HomePeriod periodOf(Calendar month, Long fundId, Long accountId, String currency) {
        Calendar start = (Calendar) month.clone();
        start.set(Calendar.DAY_OF_MONTH, 1);
        TransactionFilter.startOfDay(start);
        Calendar end = (Calendar) start.clone();
        end.add(Calendar.MONTH, 1);
        return new HomePeriod(start.getTime(), end.getTime(), fundId, accountId, currency);
    }

    /** For scans started from a screen; it blocks, so call it off the main thread. */
    public CaptureService getCapture() {
        return capture;
    }

    /****************************************************************************************************/
    // Home screen

    /** One row per currency that has anything in the month. */
    public LiveData<List<SummaryTotals>> getHomeTotals() {
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

    /** The fund Home is narrowed to, or null. */
    public Long getHomeFundId() {
        return homePeriod.getValue().fundId;
    }

    /** The account Home is narrowed to, or null. */
    public Long getHomeAccountId() {
        return homePeriod.getValue().accountId;
    }

    /** Narrows Home to one fund, or to everything when null. Any account choice is cleared. */
    public void setHomeFund(Long fundId) {
        homeFilterChosen = true;
        showHome(fundId, null);
    }

    /** Narrows Home to one account, or to everything when null. Any fund choice is cleared. */
    public void setHomeAccount(Long accountId) {
        homeFilterChosen = true;
        showHome(null, accountId);
    }

    /** Home opens on the default account, until the person picks a fund or an account themselves. */
    public void applyDefaultHomeAccount(long accountId) {
        if (!homeFilterChosen) {
            showHome(null, accountId);
        }
    }

    /** Home opens on everyday money, the default fund, until the person picks a fund or an account themselves. */
    public void applyDefaultHomeFund(long fundId) {
        if (!homeFilterChosen) {
            showHome(fundId, null);
        }
    }

    private void showHome(Long fundId, Long accountId) {
        HomePeriod current = homePeriod.getValue();
        homePeriod.setValue(new HomePeriod(current.from, current.to, fundId, accountId, current.currency));
    }

    /** Null until Home picks one: the main currency, or another the person chose. */
    public String getHomeCurrency() {
        return homePeriod.getValue().currency;
    }

    public void setHomeCurrency(String currency) {
        HomePeriod current = homePeriod.getValue();
        if (!java.util.Objects.equals(current.currency, currency)) {
            homePeriod.setValue(new HomePeriod(current.from, current.to, current.fundId, current.accountId,
                    currency));
        }
    }

    public Calendar getHomeMonth() {
        Calendar month = Calendar.getInstance();
        month.setTime(homePeriod.getValue().from);
        return month;
    }

    public void moveHomeMonth(int months) {
        Calendar month = getHomeMonth();
        month.add(Calendar.MONTH, months);
        homePeriod.setValue(periodOf(month, getHomeFundId(), getHomeAccountId(), getHomeCurrency()));
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
        f.fundId = period.fundId;
        f.accountId = period.accountId;
        f.category = category;
        f.currency = period.currency;
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
    // Fund and account details

    /** Leaves out transfers between accounts that stay in this fund, since they change nothing in it. */
    public LiveData<List<ActivityRow>> getRowsForFund(long fundId, long fromMillis, long toMillis) {
        Date from = new Date(fromMillis);
        Date to = new Date(toMillis);
        return combine(transactions.findForFund(fundId, from, to), transactions.transferLegsBetween(from, to),
                row -> !row.isTransfer() || !Transfers.staysInFund(row.debitLeg, row.creditLeg));
    }

    /** Leaves out moves between funds: the bank never saw them, so the history matches its statement. */
    public LiveData<List<ActivityRow>> getRowsForAccount(long accountId, long fromMillis, long toMillis) {
        Date from = new Date(fromMillis);
        Date to = new Date(toMillis);
        return combine(transactions.findForAccount(accountId, from, to), transactions.transferLegsBetween(from, to),
                row -> !row.isMove());
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
        lastAddedAt = android.os.SystemClock.elapsedRealtime();
        Writes.log(TAG, "inserting transaction", transactions.add(transaction));
    }

    /** True for a few seconds after the person adds something, so a list can go to where it landed. */
    public boolean justAdded() {
        return lastAddedAt != 0 && android.os.SystemClock.elapsedRealtime() - lastAddedAt < 5000;
    }

    public void updateTransaction(Transaction edited) {
        Writes.log(TAG, "updating transaction", transactions.update(edited));
    }

    public void setCategory(Transaction transaction, String category) {
        Writes.log(TAG, "choosing category", transactions.chooseCategory(transaction.getId(), category));
    }

    public void setFund(long id, long fundId) {
        Writes.log(TAG, "moving transaction to fund", transactions.setFund(id, fundId));
    }

    /** The row takes on the account's currency. */
    public void setAccount(long id, long accountId) {
        Writes.log(TAG, "moving transaction to account", transactions.setAccount(id, accountId));
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
        lastAddedAt = android.os.SystemClock.elapsedRealtime();
        Writes.log(TAG, "saving transfer", transactions.addTransfer(debit, credit));
    }

    public void updateTransfer(Transaction debit, Transaction credit) {
        Writes.log(TAG, "updating transfer", transactions.updateTransfer(debit, credit));
    }
}
