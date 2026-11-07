package com.example.expensemate.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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

import java.util.Calendar;
import java.util.Date;

/** What a captured message is filled in with from the accounts, bills and transactions already saved. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class CaptureSuggestionsTest {
    private static final long T0 = 1793447463000L;
    private static final long HOUR = 60L * 60 * 1000;

    private AppDatabase database;
    private LocalServices services;
    private long nextTime = T0;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        services = TestServices.over(database);
        services.categories().saveAsDefault(new Category("Default", "EXPENSE")).join();
        database.categoryDao().insertCategory(new Category("Food", "EXPENSE"));
    }

    @After
    public void tearDown() {
        database.close();
    }

    private Transaction captured(String counterparty, String body) {
        nextTime += HOUR;
        String text = body + " " + nextTime;
        Transaction row = new Transaction(100, text, new Date(nextTime), "DEBIT", counterparty, text, "AX-BANK-S");
        row.setSource(TransactionKeys.SOURCE_SMS_BROADCAST);
        row.setMessageTime(nextTime);
        row.setDedupKey(TransactionKeys.bodyKey(text));
        row.setNeedsReview(true);
        assertTrue(services.capture().recordIfNew(row));
        return row;
    }

    private void chosen(String counterparty, String category) {
        nextTime += HOUR;
        Transaction row = new Transaction(100, "", new Date(nextTime), "DEBIT", counterparty, null, null);
        row.setCategory(category);
        services.transactions().add(row).join();
    }

    @Test
    public void similarPayeeGetsTheirCategoryButStillWaitsForReview() {
        chosen("SWIGGY", "Food");

        Transaction row = captured("SWIGGY INSTAMART", "Rs 100 spent");
        assertEquals("Food", row.getCategory());
        assertTrue(row.isNeedsReview());
    }

    @Test
    public void unfamiliarPayeeKeepsTheDefault() {
        chosen("SWIGGY", "Food");

        assertEquals("Default", captured("RAJU MEDICALS", "Rs 100 spent").getCategory());
    }

    @Test
    public void accountNumberInTheMessagePicksTheRealAccount() {
        Account card = new Account("HDFC card", "4321", "HDFC", null, null);
        card.setType(Account.TYPE_REAL);
        database.accountDao().insert(card);
        long cardId = database.accountDao().getAllAccountsSync().get(0).getId();

        assertEquals(Long.valueOf(cardId), captured("AMAZON", "Spent Rs 100 on card XX4321").getRealAccountId());
    }

    @Test
    public void billIsSuggestedButNotLinked() {
        Calendar due = Calendar.getInstance();
        due.setTimeInMillis(nextTime + HOUR);
        RecurringPayment rent = new RecurringPayment("Rent", 100, due.get(Calendar.DAY_OF_MONTH), null);
        database.recurringPaymentDao().insert(rent);
        long rentId = database.recurringPaymentDao().getAllRecurringPaymentsSync().get(0).getId();

        Transaction row = captured("MR LANDLORD", "Rs 100 sent");
        assertNull(row.getLinkedRecurringPaymentId());
        assertEquals(Long.valueOf(rentId), services.transactions().suggestBill(row).join());
    }
}
