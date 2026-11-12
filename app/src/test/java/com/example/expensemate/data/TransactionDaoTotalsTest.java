package com.example.expensemate.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Looper;

import androidx.lifecycle.LiveData;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class TransactionDaoTotalsTest {
    private static final Date OCT = new GregorianCalendar(2026, Calendar.OCTOBER, 1).getTime();
    private static final Date NOV = new GregorianCalendar(2026, Calendar.NOVEMBER, 1).getTime();
    private static final Date MID_OCT = new GregorianCalendar(2026, Calendar.OCTOBER, 15, 12, 0).getTime();
    private static final long CHECKING = 1;
    private static final long SAVINGS = 2;

    private AppDatabase database;
    private TransactionDao dao;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        dao = database.transactionDao();
    }

    @After
    public void tearDown() {
        database.close();
    }

    private static Transaction row(double amount, String type, long accountId, Date date, String category) {
        Transaction transaction = new Transaction();
        transaction.setAmount(amount);
        transaction.setTransactionType(type);
        transaction.setAccountId(accountId);
        transaction.setDate(date);
        transaction.setCategory(category);
        return transaction;
    }

    private static <T> T read(LiveData<T> liveData) throws InterruptedException {
        @SuppressWarnings("unchecked")
        final T[] value = (T[]) new Object[1];
        androidx.lifecycle.Observer<T> observer = v -> value[0] = v;
        liveData.observeForever(observer);
        for (int i = 0; i < 200 && value[0] == null; i++) {
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            if (value[0] == null) {
                Thread.sleep(10);
            }
        }
        liveData.removeObserver(observer);
        return value[0];
    }

    /** The totals of the only currency there is, or zeros when there is nothing. */
    private static SummaryTotals only(List<SummaryTotals> perCurrency) {
        assertTrue("one currency at most", perCurrency.size() <= 1);
        return perCurrency.isEmpty() ? new SummaryTotals() : perCurrency.get(0);
    }

    private static double net(List<AccountBalance> perCurrency) {
        assertTrue("one currency at most", perCurrency.size() <= 1);
        return perCurrency.isEmpty() ? 0 : perCurrency.get(0).balance;
    }

    private static Transaction row(double amount, String type, long accountId, Date date, String category,
            String currency) {
        Transaction transaction = row(amount, type, accountId, date, category);
        transaction.setCurrency(currency);
        return transaction;
    }

    @Test
    public void totalsComeOnePerCurrencyAndAreNeverAddedTogether() throws Exception {
        dao.insertTransaction(row(1000, "DEBIT", CHECKING, MID_OCT, "Food", "INR"));
        dao.insertTransaction(row(5000, "CREDIT", CHECKING, MID_OCT, "Salary", "INR"));
        dao.insertTransaction(row(40, "DEBIT", CHECKING, MID_OCT, "Food", "AED"));

        List<SummaryTotals> totals = read(dao.getSummaryTotals(OCT, NOV, null));
        assertEquals(2, totals.size());
        assertEquals("AED", totals.get(0).currency);
        assertEquals(40, totals.get(0).expense, 0.001);
        assertEquals(0, totals.get(0).income, 0.001);
        assertEquals("INR", totals.get(1).currency);
        assertEquals(1000, totals.get(1).expense, 0.001);
        assertEquals(5000, totals.get(1).income, 0.001);

        List<CategorySum> aed = read(dao.getCategorySums("DEBIT", "AED", OCT, NOV, null));
        assertEquals(1, aed.size());
        assertEquals(40, aed.get(0).getTotal(), 0.001);
        assertEquals(1000, read(dao.getCategorySums("DEBIT", "INR", OCT, NOV, null)).get(0).getTotal(), 0.001);
    }

    @Test
    public void aVirtualAccountGetsABalanceForEachCurrencyItHolds() throws Exception {
        dao.insertTransaction(row(5000, "CREDIT", CHECKING, MID_OCT, "Salary", "INR"));
        dao.insertTransaction(row(300, "CREDIT", CHECKING, MID_OCT, "Salary", "AED"));
        dao.insertTransaction(row(100, "DEBIT", CHECKING, MID_OCT, "Food", "AED"));

        List<AccountBalance> balances = read(dao.getAccountBalances());
        assertEquals(2, balances.size());
        assertEquals("AED", balances.get(0).currency);
        assertEquals(200, balances.get(0).balance, 0.001);
        assertEquals("INR", balances.get(1).currency);
        assertEquals(5000, balances.get(1).balance, 0.001);
        assertEquals(2, read(dao.getNetBalances()).size());
    }

    @Test
    public void filterFindsOneCurrency() throws Exception {
        dao.insertTransaction(row(1000, "DEBIT", CHECKING, MID_OCT, "Food", "INR"));
        dao.insertTransaction(row(40, "DEBIT", CHECKING, MID_OCT, "Food", "AED"));

        List<Transaction> aed = read(dao.getFilteredTransactions(OCT, NOV, null, null, null, null, null, null, "AED",
                null, null, null, null, null));
        assertEquals(1, aed.size());
        assertEquals(40, aed.get(0).getAmount(), 0.001);
    }

    @Test
    public void transfersStayOutOfIncomeAndSpendingButAreReportedSeparately() throws Exception {
        dao.insertTransaction(row(1000, "DEBIT", CHECKING, MID_OCT, "Food"));
        dao.insertTransaction(row(5000, "CREDIT", CHECKING, MID_OCT, "Salary"));
        Transaction[] transfer = Transfers.newPair(2000, CHECKING, SAVINGS, MID_OCT, "");
        dao.insertTransaction(transfer[0]);
        dao.insertTransaction(transfer[1]);

        SummaryTotals all = only(read(dao.getSummaryTotals(OCT, NOV, null)));
        assertEquals(1000, all.expense, 0.001);
        assertEquals(5000, all.income, 0.001);
        assertEquals(4000, all.left(), 0.001);

        SummaryTotals checking = only(read(dao.getSummaryTotals(OCT, NOV, CHECKING)));
        assertEquals(1000, checking.expense, 0.001);
        assertEquals(2000, checking.movedOut, 0.001);
        assertEquals("What is really left in the account", 2000, checking.left(), 0.001);
        assertEquals(0, checking.movedIn, 0.001);

        SummaryTotals savings = only(read(dao.getSummaryTotals(OCT, NOV, SAVINGS)));
        assertEquals(0, savings.income, 0.001);
        assertEquals(2000, savings.movedIn, 0.001);
    }

    @Test
    public void transfersStayOutOfTheCategoryBreakdown() throws Exception {
        dao.insertTransaction(row(1000, "DEBIT", CHECKING, MID_OCT, "Food"));
        Transaction[] transfer = Transfers.newPair(2000, CHECKING, SAVINGS, MID_OCT, "");
        dao.insertTransaction(transfer[0]);
        dao.insertTransaction(transfer[1]);

        List<CategorySum> sums = read(dao.getCategorySums("DEBIT", "INR", OCT, NOV, null));
        assertEquals(1, sums.size());
        assertEquals("Food", sums.get(0).getCategory());
    }

    @Test
    public void balancesCountTransfersAndNetBalanceCancelsThemOut() throws Exception {
        dao.insertTransaction(row(5000, "CREDIT", CHECKING, MID_OCT, "Salary"));
        dao.insertTransaction(row(1000, "DEBIT", CHECKING, MID_OCT, "Food"));
        Transaction[] transfer = Transfers.newPair(2000, CHECKING, SAVINGS, MID_OCT, "");
        dao.insertTransaction(transfer[0]);
        dao.insertTransaction(transfer[1]);

        assertEquals(4000, net(read(dao.getNetBalances())), 0.001);
        double checking = 0;
        double savings = 0;
        for (AccountBalance balance : read(dao.getAccountBalances())) {
            if (balance.accountId == CHECKING) {
                checking = balance.balance;
            } else if (balance.accountId == SAVINGS) {
                savings = balance.balance;
            }
        }
        assertEquals(2000, checking, 0.001);
        assertEquals(2000, savings, 0.001);
    }

    @Test
    public void excludedRowsStayOutOfTotals() throws Exception {
        Transaction excluded = row(1000, "DEBIT", CHECKING, MID_OCT, "Food");
        excluded.setExcludedFromSummary(true);
        dao.insertTransaction(excluded);

        assertEquals(0, only(read(dao.getSummaryTotals(OCT, NOV, null))).expense, 0.001);
        assertEquals(0, net(read(dao.getNetBalances())), 0.001);
    }

    @Test
    public void monthsFollowLocalTimeNotUtc() throws Exception {
        TimeZone original = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
        try {
            Calendar justAfterMidnight = new GregorianCalendar(2026, Calendar.NOVEMBER, 1, 0, 30);
            Calendar start = new GregorianCalendar(2026, Calendar.NOVEMBER, 1);
            Calendar end = new GregorianCalendar(2026, Calendar.DECEMBER, 1);
            Calendar octStart = new GregorianCalendar(2026, Calendar.OCTOBER, 1);
            dao.insertTransaction(row(100, "DEBIT", CHECKING, justAfterMidnight.getTime(), "Food"));

            assertEquals(100, only(read(dao.getSummaryTotals(start.getTime(), end.getTime(), null))).expense, 0.001);
            assertEquals(0, only(read(dao.getSummaryTotals(octStart.getTime(), start.getTime(), null))).expense, 0.001);
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    public void editingKeepsTheExcludedFlagAndMessageKeys() throws Exception {
        Transaction row = row(100, "DEBIT", CHECKING, MID_OCT, "Food");
        row.setId(7);
        row.setExcludedFromSummary(true);
        row.setDedupKey("dedup");
        row.setMatchKey("match");
        row.setSource(TransactionKeys.SOURCE_SMS_SCAN);
        row.setNeedsReview(true);
        dao.insertTransaction(row);

        Transaction edited = row.copy();
        edited.setAmount(250);
        edited.setNeedsReview(false);
        dao.updateRow(edited);

        Transaction saved = read(dao.getTransaction(7));
        assertEquals(250, saved.getAmount(), 0.001);
        assertTrue(saved.isExcludedFromSummary());
        assertFalse(saved.isNeedsReview());
        assertEquals("dedup", saved.getDedupKey());
        assertEquals("match", saved.getMatchKey());
    }

    @Test
    public void choosingACategoryClearsTheReviewFlag() throws Exception {
        Transaction row = row(100, "DEBIT", CHECKING, MID_OCT, "Default");
        row.setId(9);
        row.setNeedsReview(true);
        dao.insertTransaction(row);
        assertEquals(Integer.valueOf(1), read(dao.getNeedsReviewCount()));

        dao.setCategory(9, "Food");

        assertEquals(Integer.valueOf(0), read(dao.getNeedsReviewCount()));
        assertEquals("Food", read(dao.getTransaction(9)).getCategory());
    }

    @Test
    public void deletingOneSideOfATransferRemovesBothAndRestoreBringsThemBack() throws Exception {
        Transaction[] transfer = Transfers.newPair(2000, CHECKING, SAVINGS, MID_OCT, "");
        dao.insertTransaction(transfer[0]);
        dao.insertTransaction(transfer[1]);
        String group = transfer[0].getTransferGroupId();
        List<Transaction> legs = dao.getTransferLegsSync(group);
        assertEquals(2, legs.size());

        dao.deleteTransfer(group);
        assertEquals(0, dao.getTransferLegsSync(group).size());

        for (Transaction leg : legs) {
            dao.insertTransaction(leg);
        }
        assertEquals(2, dao.getTransferLegsSync(group).size());
    }

    @Test
    public void updatingATransferChangesBothSidesAndKeepsTheLink() throws Exception {
        Transaction[] transfer = Transfers.newPair(2000, CHECKING, SAVINGS, MID_OCT, "");
        dao.insertTransaction(transfer[0]);
        dao.insertTransaction(transfer[1]);
        String group = transfer[0].getTransferGroupId();
        List<Transaction> legs = dao.getTransferLegsSync(group);

        Transaction debit = legs.get(0).copy();
        Transaction credit = legs.get(1).copy();
        debit.setAmount(3000);
        credit.setAmount(3000);
        dao.updateRow(debit);
        dao.updateRow(credit);

        for (Transaction leg : dao.getTransferLegsSync(group)) {
            assertEquals(3000, leg.getAmount(), 0.001);
            assertEquals(group, leg.getTransferGroupId());
        }
    }

    @Test
    public void filtersFindTransfersAndNormalRowsSeparately() throws Exception {
        dao.insertTransaction(row(1000, "DEBIT", CHECKING, MID_OCT, "Food"));
        Transaction[] transfer = Transfers.newPair(2000, CHECKING, SAVINGS, MID_OCT, "");
        dao.insertTransaction(transfer[0]);
        dao.insertTransaction(transfer[1]);

        assertEquals(2, read(dao.getFilteredTransactions(OCT, NOV, null, null, null, null, null, null, null, null, null, null,
                true, null)).size());
        assertEquals(1, read(dao.getFilteredTransactions(OCT, NOV, null, null, null, null, null, null, null, "DEBIT", null,
                null, false, null)).size());
        assertEquals(3, read(dao.getFilteredTransactions(OCT, NOV, null, null, null, null, null, null, null, null, null, null,
                null, null)).size());
    }

    @Test
    public void searchMatchesNameNoteAndAmount() throws Exception {
        Transaction row = row(1240, "DEBIT", CHECKING, MID_OCT, "Food");
        row.setReceiverName("Corner Grocery");
        row.setDescription("weekly shop");
        dao.insertTransaction(row);
        dao.insertTransaction(row(55, "DEBIT", CHECKING, MID_OCT, "Food"));

        for (String search : new String[] { "corner", "weekly", "1240" }) {
            assertEquals(search, 1, read(dao.getFilteredTransactions(OCT, NOV, null, search, null, null, null, null,
                    null, null, null, null, null, null)).size());
        }
    }
}
