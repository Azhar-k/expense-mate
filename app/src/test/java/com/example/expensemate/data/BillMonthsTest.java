package com.example.expensemate.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.example.expensemate.domain.local.LocalServices;
import com.example.expensemate.util.Periods;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Date;
import java.util.List;

/** Paying, skipping and linking each mark one month of a bill, and undoing them unmarks only that month. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class BillMonthsTest {
    private AppDatabase database;
    private LocalServices services;
    private RecurringPaymentDao dao;
    private final int now = Periods.current();

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).allowMainThreadQueries().build();
        services = TestServices.over(database);
        dao = database.recurringPaymentDao();
        Fund fund = new Fund("Daily", "");
        fund.setDefault(true);
        services.funds().add(fund).join();
        Account account = new Account("Bank", "", "", null, "");
        account.setCurrency("INR");
        account.setDefault(true);
        services.accounts().add(account).join();
    }

    @After
    public void tearDown() {
        database.close();
    }

    private RecurringPayment addBill(int startPeriod) {
        RecurringPayment bill = new RecurringPayment("Rent", 15000, 1, new Date(Long.MAX_VALUE / 2));
        bill.setCurrency("INR");
        bill.setStartPeriod(startPeriod);
        services.bills().add(bill).join();
        return dao.getAllRecurringPaymentsSync().get(0);
    }

    private Transaction pay(RecurringPayment bill, double amount) {
        Transaction row = new Transaction();
        row.setAmount(amount);
        row.setTransactionType("DEBIT");
        row.setDate(new Date());
        row.setCurrency("INR");
        row.setLinkedRecurringPaymentId(bill.getId());
        services.transactions().add(row).join();
        return row;
    }

    @Test
    public void aNewBillIsOwedFromThisMonth() {
        RecurringPayment bill = addBill(0);
        assertEquals(now, bill.getStartPeriod());
    }

    @Test
    public void markingPaidSkippingAndUndoingTouchOneMonth() {
        RecurringPayment bill = addBill(0);
        int last = Periods.plus(now, -1);
        services.bills().markPaid(bill, now).join();
        services.bills().skip(bill, last).join();
        assertTrue(dao.getPaymentSync(bill.getId(), last).isSkipped());
        assertEquals(15000, dao.getPaymentSync(bill.getId(), now).getAmount(), 0);

        services.bills().resetPaid(bill, now).join();
        assertNull(dao.getPaymentSync(bill.getId(), now));
        assertNotNull("Last month is left alone", dao.getPaymentSync(bill.getId(), last));
    }

    @Test
    public void aLinkedPaymentPaysItsMonthAndThenTheOldestOneOwed() {
        int twoBack = Periods.plus(now, -2);
        RecurringPayment bill = addBill(twoBack);

        Transaction first = pay(bill, 14800);
        BillPayment thisMonth = dao.getPaymentSync(bill.getId(), now);
        assertEquals(Long.valueOf(first.getId()), thisMonth.getTransactionId());
        assertEquals("What was actually paid", 14800, thisMonth.getAmount(), 0);

        // This month is done, so the next payment catches up the oldest missed one
        Transaction second = pay(bill, 15000);
        assertEquals(Long.valueOf(second.getId()), dao.getPaymentSync(bill.getId(), twoBack).getTransactionId());
        assertNull(dao.getPaymentSync(bill.getId(), Periods.plus(now, -1)));
    }

    @Test
    public void deletingOrUnlinkingThePaymentUnmarksItsMonth() {
        RecurringPayment bill = addBill(0);
        Transaction row = pay(bill, 15000);

        List<Transaction> removed = services.transactions().delete(row.getId()).join();
        assertNull(dao.getPaymentSync(bill.getId(), now));
        services.transactions().restore(removed).join();
        assertNotNull("Undo brings the month back", dao.getPaymentSync(bill.getId(), now));

        Transaction saved = database.transactionDao().getTransactionSync(row.getId());
        saved.setLinkedRecurringPaymentId(null);
        services.transactions().update(saved).join();
        assertNull(dao.getPaymentSync(bill.getId(), now));
    }

    @Test
    public void deletingABillDropsItsMonths() {
        RecurringPayment bill = addBill(0);
        services.bills().markPaid(bill, now).join();
        services.bills().delete(bill).join();
        assertTrue(dao.getAllPaymentsSync().isEmpty());
    }
}
