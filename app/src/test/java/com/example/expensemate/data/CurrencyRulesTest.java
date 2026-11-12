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

/** A transaction's amount is always in its real account's currency, and currencies are never added together. */
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
        daily = account("Daily", Account.TYPE_VIRTUAL, null, true);
        rupees = account("HDFC", Account.TYPE_REAL, "INR", true);
        dirhams = account("Emirates NBD", Account.TYPE_REAL, "AED", false);
    }

    @After
    public void tearDown() {
        database.close();
    }

    private long account(String name, String type, String currency, boolean isDefault) {
        Account account = new Account(name, "", "", null, "");
        account.setType(type);
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

    private Transaction expense(double amount, Long realAccountId) {
        Transaction row = new Transaction();
        row.setAmount(amount);
        row.setTransactionType("DEBIT");
        row.setDate(new Date(1000));
        row.setAccountId(daily);
        row.setRealAccountId(realAccountId);
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
    public void aTransactionTakesItsRealAccountsCurrency() {
        services.transactions().add(expense(40, dirhams)).join();
        assertEquals("AED", only().getCurrency());
    }

    @Test
    public void withoutARealAccountItGoesToTheDefaultAndItsCurrency() {
        services.transactions().add(expense(40, null)).join();
        assertEquals(rupees, (long) only().getRealAccountId());
        assertEquals("INR", only().getCurrency());
    }

    @Test
    public void movingToAnotherRealAccountChangesTheCurrency() {
        services.transactions().add(expense(40, rupees)).join();
        services.transactions().setRealAccount(only().getId(), dirhams).join();
        assertEquals("AED", only().getCurrency());

        Transaction edited = only().copy();
        edited.setRealAccountId(rupees);
        services.transactions().update(edited).join();
        assertEquals("INR", only().getCurrency());
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
    public void deletingARealAccountMovesItsRowsIntoTheDefaultsCurrency() {
        services.transactions().add(expense(40, dirhams)).join();
        services.accounts().delete(database.accountDao().getAccountByIdSync(dirhams)).join();
        assertEquals(rupees, (long) only().getRealAccountId());
        assertEquals("INR", only().getCurrency());
    }

    @Test
    public void aVirtualAccountKeepsNoCurrencyAndARealOneAlwaysHasOne() {
        assertNull(database.accountDao().getAccountByIdSync(daily).getCurrency());
        long noneGiven = account("Cash", Account.TYPE_REAL, null, false);
        assertEquals("The main currency", "INR", database.accountDao().getAccountByIdSync(noneGiven).getCurrency());
    }

    @Test
    public void aTransferBetweenCurrenciesKeepsBothAmounts() {
        Transaction[] legs = Transfers.newPair(1000, daily, daily, new Date(1000), "");
        legs[0].setRealAccountId(dirhams);
        legs[1].setRealAccountId(rupees);
        legs[1].setAmount(22700);
        services.transactions().addTransfer(legs[0], legs[1]).join();

        double aed = 0;
        double inr = 0;
        for (AccountBalance balance : read(database.transactionDao().getAccountBalances())) {
            if (balance.accountId == dirhams) {
                aed = balance.balance;
            } else if (balance.accountId == rupees) {
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
        bill.setFromAccountId(daily);
        bill.setToAccountId(account("Goals", Account.TYPE_VIRTUAL, null, false));
        bill.setCurrency("AED");
        services.bills().add(bill).join();
        bill = database.recurringPaymentDao().getAllRecurringPaymentsSync().get(0);

        services.bills().payByTransfer(bill).join();
        for (Transaction leg : database.transactionDao().getAllTransactionsSyncOrderByDateAsc()) {
            assertEquals("AED", leg.getCurrency());
            assertEquals("Held in the account that has dirhams", dirhams, (long) leg.getRealAccountId());
        }
    }
}
