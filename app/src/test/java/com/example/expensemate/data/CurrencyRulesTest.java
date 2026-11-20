package com.example.expensemate.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.example.expensemate.domain.local.LocalServices;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Date;
import java.util.List;

/** A transaction's amount is always in its account's currency, and currencies are never added together. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class CurrencyRulesTest {
    private AppDatabase database;
    private LocalServices services;
    private long daily;
    private long rupees;
    private long dirhams;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).allowMainThreadQueries().build();
        services = TestServices.over(database);
        daily = fund("Daily", true);
        rupees = account("HDFC", "INR", true);
        dirhams = account("Emirates NBD", "AED", false);
    }

    @After
    public void tearDown() {
        database.close();
    }

    private long fund(String name, boolean isDefault) {
        Fund fund = new Fund(name, "");
        fund.setDefault(isDefault);
        services.funds().add(fund).join();
        for (Fund saved : database.fundDao().getAllFundsSync()) {
            if (saved.getName().equals(name)) {
                return saved.getId();
            }
        }
        throw new AssertionError(name);
    }

    private long account(String name, String currency, boolean isDefault) {
        Account account = new Account(name, "", "", null, "");
        account.setCurrency(currency);
        account.setDefault(isDefault);
        services.accounts().add(account).join();
        for (Account saved : database.accountDao().getAllAccountsSync()) {
            if (saved.getName().equals(name)) {
                return saved.getId();
            }
        }
        throw new AssertionError(name);
    }

    private Transaction expense(double amount, Long accountId) {
        Transaction row = new Transaction();
        row.setAmount(amount);
        row.setTransactionType("DEBIT");
        row.setDate(new Date(1000));
        row.setFundId(daily);
        row.setAccountId(accountId);
        return row;
    }

    private static <T> T read(androidx.lifecycle.LiveData<T> liveData) {
        @SuppressWarnings("unchecked")
        final T[] value = (T[]) new Object[1];
        androidx.lifecycle.Observer<T> observer = v -> value[0] = v;
        liveData.observeForever(observer);
        for (int i = 0; i < 200 && value[0] == null; i++) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            if (value[0] == null) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    throw new AssertionError(e);
                }
            }
        }
        liveData.removeObserver(observer);
        return value[0];
    }

    private Transaction only() {
        List<Transaction> rows = database.transactionDao().getAllTransactionsSyncOrderByDateAsc();
        assertEquals(1, rows.size());
        return rows.get(0);
    }

    @Test
    public void aTransactionTakesItsAccountsCurrency() {
        services.transactions().add(expense(40, dirhams)).join();
        assertEquals("AED", only().getCurrency());
    }

    @Test
    public void withoutAnAccountItGoesToTheDefaultAndItsCurrency() {
        services.transactions().add(expense(40, null)).join();
        assertEquals(rupees, (long) only().getAccountId());
        assertEquals("INR", only().getCurrency());
    }

    @Test
    public void withoutAFundItGoesToTheDefaultFund() {
        Transaction row = expense(40, rupees);
        row.setFundId(null);
        services.transactions().add(row).join();
        assertEquals(daily, (long) only().getFundId());
    }

    @Test
    public void movingToAnotherAccountChangesTheCurrency() {
        services.transactions().add(expense(40, rupees)).join();
        services.transactions().setAccount(only().getId(), dirhams).join();
        assertEquals("AED", only().getCurrency());
        assertEquals("The fund stays", daily, (long) only().getFundId());

        Transaction edited = only().copy();
        edited.setAccountId(rupees);
        services.transactions().update(edited).join();
        assertEquals("INR", only().getCurrency());
    }

    @Test
    public void movingToAnotherFundKeepsTheAccountAndCurrency() {
        long trip = fund("Trip", false);
        services.transactions().add(expense(40, dirhams)).join();
        services.transactions().setFund(only().getId(), trip).join();
        assertEquals(trip, (long) only().getFundId());
        assertEquals(dirhams, (long) only().getAccountId());
        assertEquals("AED", only().getCurrency());
    }

    @Test
    public void changingAnAccountsCurrencyRelabelsWithoutConverting() {
        services.transactions().add(expense(40, dirhams)).join();
        Account account = database.accountDao().getAccountByIdSync(dirhams);
        account.setCurrency("USD");
        services.accounts().update(account).join();
        assertEquals("USD", only().getCurrency());
        assertEquals(40, only().getAmount(), 0.001);
    }

    @Test
    public void deletingAnAccountMovesItsRowsIntoTheDefaultsCurrency() {
        services.transactions().add(expense(40, dirhams)).join();
        services.accounts().delete(database.accountDao().getAccountByIdSync(dirhams)).join();
        assertEquals(rupees, (long) only().getAccountId());
        assertEquals("INR", only().getCurrency());
        assertEquals("The fund stays", daily, (long) only().getFundId());
    }

    @Test
    public void deletingTheDefaultAccountOrFundDoesNothing() {
        services.accounts().delete(database.accountDao().getAccountByIdSync(rupees)).join();
        services.funds().delete(database.fundDao().getFundByIdSync(daily)).join();
        assertEquals(2, database.accountDao().getAllAccountsSync().size());
        assertEquals(1, database.fundDao().getAllFundsSync().size());
    }

    @Test
    public void deletingAFundMovesItsRowsAndTopUpsToTheDefault() {
        long trip = fund("Trip", false);
        long goals = fund("Goals", false);
        Transaction row = expense(40, dirhams);
        row.setFundId(trip);
        services.transactions().add(row).join();
        RecurringPayment out = new RecurringPayment("Out of trip", 300, 1, null);
        out.setFromFundId(trip);
        out.setToFundId(goals);
        services.bills().add(out).join();
        RecurringPayment in = new RecurringPayment("Into trip", 100, 1, null);
        in.setFromFundId(goals);
        in.setToFundId(trip);
        services.bills().add(in).join();

        services.funds().delete(database.fundDao().getFundByIdSync(trip)).join();

        assertNull(database.fundDao().getFundByIdSync(trip));
        assertEquals(daily, (long) only().getFundId());
        assertEquals("The account and currency stay", dirhams, (long) only().getAccountId());
        assertEquals("AED", only().getCurrency());
        for (RecurringPayment bill : database.recurringPaymentDao().getAllRecurringPaymentsSync()) {
            if (bill.getName().equals("Out of trip")) {
                assertEquals(daily, (long) bill.getFromFundId());
                assertEquals(goals, (long) bill.getToFundId());
            } else {
                assertEquals(goals, (long) bill.getFromFundId());
                assertEquals(daily, (long) bill.getToFundId());
            }
        }
    }

    @Test
    public void aFundAndAnAccountWithTheSameIdStayApart() {
        Fund trip = new Fund("Trip", "");
        trip.setId(50);
        database.fundDao().insert(trip);
        Account wallet = new Account("Wallet", "", "", null, "");
        wallet.setId(50);
        wallet.setCurrency("AED");
        database.accountDao().insert(wallet);
        Transaction row = expense(40, 50L);
        row.setFundId(50L);
        services.transactions().add(row).join();

        services.funds().delete(database.fundDao().getFundByIdSync(50)).join();
        assertEquals(daily, (long) only().getFundId());
        assertEquals("The account with the same id stays", 50, (long) only().getAccountId());
        assertEquals("AED", only().getCurrency());

        services.accounts().delete(database.accountDao().getAccountByIdSync(50)).join();
        assertEquals(rupees, (long) only().getAccountId());
        assertEquals(daily, (long) only().getFundId());
    }

    @Test
    public void anAccountAlwaysHasACurrency() {
        long noneGiven = account("Cash", null, false);
        assertEquals("The main currency", "INR", database.accountDao().getAccountByIdSync(noneGiven).getCurrency());
    }

    @Test
    public void aTransferBetweenCurrenciesKeepsBothAmounts() {
        Transaction[] legs = Transfers.newPair(1000, daily, daily, new Date(1000), "");
        legs[0].setAccountId(dirhams);
        legs[1].setAccountId(rupees);
        legs[1].setAmount(22700);
        services.transactions().addTransfer(legs[0], legs[1]).join();

        double aed = 0;
        double inr = 0;
        for (Balance balance : read(database.transactionDao().getAccountBalances())) {
            if (balance.id == dirhams) {
                aed = balance.balance;
            } else if (balance.id == rupees) {
                inr = balance.balance;
            }
        }
        assertEquals(-1000, aed, 0.001);
        assertEquals(22700, inr, 0.001);
    }

    @Test
    public void onlyReturnsInTheLoansCurrencyCount() {
        Loan loan = new Loan();
        loan.setPerson("Ravi");
        loan.setAmount(500);
        loan.setCurrency("INR");
        long loanId = services.loans().add(loan).join();

        Transaction inDirhams = expense(20, dirhams);
        inDirhams.setTransactionType("CREDIT");
        services.transactions().add(inDirhams).join();
        services.loans().link(only().getId(), loanId).join();

        assertEquals(0, database.loanDao().getLoanSummariesSync().get(0).returned, 0.001);
    }

    @Test
    public void aPlannedTransferStaysInTheBillsCurrency() {
        RecurringPayment bill = new RecurringPayment("Savings", 300, 1, new Date(Long.MAX_VALUE / 2));
        bill.setFromFundId(daily);
        bill.setToFundId(fund("Goals", false));
        bill.setCurrency("AED");
        services.bills().add(bill).join();
        bill = database.recurringPaymentDao().getAllRecurringPaymentsSync().get(0);

        services.bills().payByTransfer(bill).join();
        for (Transaction leg : database.transactionDao().getAllTransactionsSyncOrderByDateAsc()) {
            assertEquals("AED", leg.getCurrency());
            assertEquals("Held in the account that has dirhams", dirhams, (long) leg.getAccountId());
        }
    }
}
