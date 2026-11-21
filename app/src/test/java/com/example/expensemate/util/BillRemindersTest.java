package com.example.expensemate.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.expensemate.data.BillPayment;
import com.example.expensemate.data.RecurringPayment;

import org.junit.Test;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

public class BillRemindersTest {
    private static final Calendar TODAY = new GregorianCalendar(2026, Calendar.OCTOBER, 12, 9, 0);
    private static final Date FAR_FUTURE = new GregorianCalendar(2030, Calendar.JANUARY, 1).getTime();
    private static long nextId = 1;

    private static RecurringPayment bill(String name, int dueDay) {
        RecurringPayment bill = new RecurringPayment(name, 100, dueDay, FAR_FUTURE);
        bill.setId(nextId++);
        bill.setStartPeriod(202601);
        return bill;
    }

    private static List<BillReminders.Reminder> on(BillLedger ledger, RecurringPayment... bills) {
        return BillReminders.on(Arrays.asList(bills), ledger, TODAY, 3);
    }

    @Test
    public void remindsAheadOnTheDayAndTheDayAfter() {
        List<BillReminders.Reminder> reminders = on(BillLedger.EMPTY,
                bill("Ahead", 15), bill("Today", 12), bill("Late", 11), bill("Not yet", 16), bill("Long late", 5));
        assertEquals(3, reminders.size());
        assertEquals(BillReminders.Kind.AHEAD, reminders.get(0).kind);
        assertEquals("Ahead is due in 3 days", BillReminders.title(reminders.get(0)));
        assertEquals("Today is due today", BillReminders.title(reminders.get(1)));
        assertEquals("Late was due yesterday", BillReminders.title(reminders.get(2)));
    }

    @Test
    public void paidSkippedAndTopUpsGetNone() {
        RecurringPayment paid = bill("Paid", 12);
        RecurringPayment skipped = bill("Skipped", 12);
        RecurringPayment topUp = bill("Savings", 12);
        topUp.setFromFundId(1L);
        topUp.setToFundId(2L);
        BillLedger ledger = BillLedger.of(Arrays.asList(
                BillPayment.paid(paid, 202610, new Date(), null), BillPayment.skipped(skipped, 202610)));
        assertTrue(on(ledger, paid, skipped, topUp).isEmpty());
    }

    @Test
    public void aDueDateEarlyNextMonthIsRemindedThisMonth() {
        Calendar endOfMonth = new GregorianCalendar(2026, Calendar.OCTOBER, 30, 9, 0);
        List<BillReminders.Reminder> reminders = BillReminders.on(Collections.singletonList(bill("Rent", 2)),
                BillLedger.EMPTY, endOfMonth, 3);
        assertEquals(1, reminders.size());
        assertEquals(202611, reminders.get(0).period);
    }

    @Test
    public void onlyOnTheDueDayWhenNoDaysAhead() {
        assertTrue(BillReminders.on(Collections.singletonList(bill("Ahead", 15)), BillLedger.EMPTY, TODAY, 0)
                .isEmpty());
    }
}
