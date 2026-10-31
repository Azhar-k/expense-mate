package com.example.expensemate.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.expensemate.data.RecurringPayment;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

public class BillsTest {
    private static final Calendar TODAY = new GregorianCalendar(2026, Calendar.OCTOBER, 12, 10, 30);
    private static final Date FAR_FUTURE = new GregorianCalendar(2030, Calendar.JANUARY, 1).getTime();

    private static RecurringPayment bill(String name, int dueDay) {
        return new RecurringPayment(name, 100, dueDay, FAR_FUTURE);
    }

    private static Date date(int year, int month, int day) {
        return new GregorianCalendar(year, month, day, 9, 0).getTime();
    }

    @Test
    public void paidOnlyCountsForTheMonthItHappened() {
        RecurringPayment bill = bill("Rent", 1);
        bill.setCompleted(true);
        bill.setLastCompletedDate(date(2026, Calendar.OCTOBER, 1));
        assertTrue(Bills.isPaid(bill, TODAY));

        // The same record starts unpaid again as soon as November begins
        Calendar nextMonth = new GregorianCalendar(2026, Calendar.NOVEMBER, 1, 0, 5);
        assertFalse(Bills.isPaid(bill, nextMonth));

        bill.setLastCompletedDate(date(2025, Calendar.OCTOBER, 1));
        assertFalse("Same month last year", Bills.isPaid(bill, TODAY));
    }

    @Test
    public void uncheckedOrUndatedIsNotPaid() {
        RecurringPayment bill = bill("Rent", 1);
        bill.setLastCompletedDate(date(2026, Calendar.OCTOBER, 1));
        assertFalse(Bills.isPaid(bill, TODAY));

        bill.setCompleted(true);
        bill.setLastCompletedDate(null);
        assertFalse(Bills.isPaid(bill, TODAY));
    }

    @Test
    public void statusFollowsTheDueDate() {
        assertEquals(Bills.Status.LATE, Bills.statusOf(bill("Phone", 8), TODAY));
        assertEquals(Bills.Status.DUE_SOON, Bills.statusOf(bill("Today", 12), TODAY));
        assertEquals(Bills.Status.DUE_SOON, Bills.statusOf(bill("Soon", 19), TODAY));
        assertEquals(Bills.Status.UPCOMING, Bills.statusOf(bill("Later", 20), TODAY));
    }

    @Test
    public void dueDayMovesToTheLastDayOfAShortMonth() {
        Calendar february = new GregorianCalendar(2026, Calendar.FEBRUARY, 10);
        assertEquals(28, Bills.dueDate(bill("Rent", 31), february).get(Calendar.DAY_OF_MONTH));
        assertEquals(30, Bills.dueDate(bill("Rent", 31), new GregorianCalendar(2026, Calendar.APRIL, 1))
                .get(Calendar.DAY_OF_MONTH));
    }

    @Test
    public void endedWinsOverEveryOtherStatus() {
        RecurringPayment bill = new RecurringPayment("Old", 100, 1, date(2026, Calendar.OCTOBER, 11));
        bill.setCompleted(true);
        bill.setLastCompletedDate(date(2026, Calendar.OCTOBER, 1));
        assertEquals(Bills.Status.ENDED, Bills.statusOf(bill, TODAY));
    }

    @Test
    public void aBillEndingTodayHasNotEndedYet() {
        RecurringPayment bill = new RecurringPayment("Last", 100, 20, date(2026, Calendar.OCTOBER, 12));
        assertFalse(Bills.hasEnded(bill, TODAY));
    }

    @Test
    public void sortsLateThenByDueDateWithPaidAndEndedLast() {
        RecurringPayment paid = bill("Paid", 1);
        paid.setCompleted(true);
        paid.setLastCompletedDate(date(2026, Calendar.OCTOBER, 1));
        RecurringPayment ended = new RecurringPayment("Ended", 100, 2, date(2026, Calendar.JANUARY, 1));

        List<RecurringPayment> bills = new ArrayList<>();
        bills.add(ended);
        bills.add(paid);
        bills.add(bill("Later", 25));
        bills.add(bill("Soon", 15));
        bills.add(bill("Late", 8));

        List<RecurringPayment> sorted = Bills.sortByUrgency(bills, TODAY);
        assertEquals("Late", sorted.get(0).getName());
        assertEquals("Soon", sorted.get(1).getName());
        assertEquals("Later", sorted.get(2).getName());
        assertEquals("Paid", sorted.get(3).getName());
        assertEquals("Ended", sorted.get(4).getName());
    }
}
