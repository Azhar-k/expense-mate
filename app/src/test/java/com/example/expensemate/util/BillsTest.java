package com.example.expensemate.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.expensemate.data.BillPayment;
import com.example.expensemate.data.RecurringPayment;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

public class BillsTest {
    private static final Calendar TODAY = new GregorianCalendar(2026, Calendar.OCTOBER, 12, 10, 30);
    private static final int OCTOBER = 202610;
    private static final int SEPTEMBER = 202609;
    private static final Date FAR_FUTURE = new GregorianCalendar(2030, Calendar.JANUARY, 1).getTime();
    private static long nextId = 1;

    private static RecurringPayment bill(String name, int dueDay) {
        RecurringPayment bill = new RecurringPayment(name, 100, dueDay, FAR_FUTURE);
        bill.setId(nextId++);
        bill.setStartPeriod(202601);
        return bill;
    }

    private static Date date(int year, int month, int day) {
        return new GregorianCalendar(year, month, day, 9, 0).getTime();
    }

    private static BillLedger ledger(BillPayment... payments) {
        return BillLedger.of(Arrays.asList(payments));
    }

    private static BillPayment paid(RecurringPayment bill, int period) {
        return BillPayment.paid(bill, period, date(2026, Calendar.OCTOBER, 1), null);
    }

    @Test
    public void eachMonthIsPaidOnItsOwn() {
        RecurringPayment rent = bill("Rent", 1);
        BillLedger ledger = ledger(paid(rent, OCTOBER));
        assertEquals(Bills.Status.PAID, Bills.statusOf(rent, ledger, OCTOBER, TODAY));

        // November is owed whatever October did
        Calendar november = new GregorianCalendar(2026, Calendar.NOVEMBER, 1, 0, 5);
        assertEquals(Bills.Status.DUE_SOON, Bills.statusOf(rent, ledger, 202611, november));
        // And September, never paid, stays missed instead of starting over
        assertEquals(Bills.Status.LATE, Bills.statusOf(rent, ledger, SEPTEMBER, TODAY));
    }

    @Test
    public void aSkippedMonthIsDoneButNotPaid() {
        RecurringPayment gym = bill("Gym", 5);
        BillLedger ledger = ledger(BillPayment.skipped(gym, OCTOBER));
        Bills.Status status = Bills.statusOf(gym, ledger, OCTOBER, TODAY);
        assertEquals(Bills.Status.SKIPPED, status);
        assertTrue(Bills.isDone(status));
        assertFalse(Bills.isOwed(status));
    }

    @Test
    public void statusFollowsTheDueDate() {
        assertEquals(Bills.Status.LATE, Bills.statusOf(bill("Phone", 8), BillLedger.EMPTY, OCTOBER, TODAY));
        assertEquals(Bills.Status.DUE_SOON, Bills.statusOf(bill("Today", 12), BillLedger.EMPTY, OCTOBER, TODAY));
        assertEquals(Bills.Status.DUE_SOON, Bills.statusOf(bill("Soon", 19), BillLedger.EMPTY, OCTOBER, TODAY));
        assertEquals(Bills.Status.UPCOMING, Bills.statusOf(bill("Later", 20), BillLedger.EMPTY, OCTOBER, TODAY));
        assertEquals("Next month's is still to come", Bills.Status.UPCOMING,
                Bills.statusOf(bill("Phone", 8), BillLedger.EMPTY, 202611, TODAY));
        assertEquals(27, Bills.daysUntilDue(bill("Phone", 8), 202611, TODAY));
    }

    @Test
    public void dueDayMovesToTheLastDayOfAShortMonth() {
        assertEquals(28, Bills.dueDate(bill("Rent", 31), 202602).get(Calendar.DAY_OF_MONTH));
        assertEquals(30, Bills.dueDate(bill("Rent", 31), 202604).get(Calendar.DAY_OF_MONTH));
    }

    @Test
    public void aBillIsOwedFromItsStartMonthOnly() {
        RecurringPayment rent = bill("Rent", 1);
        rent.setStartPeriod(OCTOBER);
        assertEquals(Bills.Status.NOT_STARTED, Bills.statusOf(rent, BillLedger.EMPTY, SEPTEMBER, TODAY));
        assertEquals(Bills.Status.LATE, Bills.statusOf(rent, BillLedger.EMPTY, OCTOBER, TODAY));
        // A payment from before then still shows
        assertEquals(Bills.Status.PAID, Bills.statusOf(rent, ledger(paid(rent, SEPTEMBER)), SEPTEMBER, TODAY));
    }

    @Test
    public void aBillHasEndedForMonthsDueAfterItsEnd() {
        RecurringPayment old = new RecurringPayment("Old", 100, 1, date(2026, Calendar.OCTOBER, 11));
        old.setId(nextId++);
        old.setStartPeriod(202601);
        // October's was due on the 1st, before it ended, so October is still owed
        assertEquals(Bills.Status.LATE, Bills.statusOf(old, BillLedger.EMPTY, OCTOBER, TODAY));
        assertEquals(Bills.Status.ENDED, Bills.statusOf(old, BillLedger.EMPTY, 202611, TODAY));
        assertTrue(Bills.hasEnded(old, 202611));
    }

    @Test
    public void aBillEndingOnItsDueDayIsStillOwedThatMonth() {
        RecurringPayment last = new RecurringPayment("Last", 100, 20, date(2026, Calendar.OCTOBER, 20));
        assertFalse(Bills.hasEnded(last, OCTOBER));
    }

    @Test
    public void missedMonthsAreListedNewestFirst() {
        RecurringPayment rent = bill("Rent", 1);
        rent.setStartPeriod(202607);
        BillLedger ledger = ledger(paid(rent, 202608), BillPayment.skipped(rent, SEPTEMBER));
        List<Bills.Missed> missed = Bills.missedBefore(Collections.singletonList(rent), ledger, OCTOBER, TODAY);
        assertEquals(1, missed.size());
        assertEquals(202607, missed.get(0).period);
    }

    @Test
    public void aPaymentPaysItsOwnMonthOrTheOldestOneOwed() {
        RecurringPayment rent = bill("Rent", 1);
        rent.setStartPeriod(202608);
        assertEquals(OCTOBER, Bills.periodToPay(rent, BillLedger.EMPTY, TODAY));

        BillLedger octoberPaid = ledger(paid(rent, OCTOBER));
        assertEquals("August was never paid", 202608, Bills.periodToPay(rent, octoberPaid, TODAY));

        BillLedger allPaid = ledger(paid(rent, 202608), paid(rent, SEPTEMBER), paid(rent, OCTOBER));
        assertEquals("Paying ahead", 202611, Bills.periodToPay(rent, allPaid, TODAY));
    }

    @Test
    public void sortsLateThenByDueDateWithDoneAndEndedLast() {
        RecurringPayment paid = bill("Paid", 1);
        RecurringPayment ended = new RecurringPayment("Ended", 100, 2, date(2026, Calendar.JANUARY, 1));
        ended.setId(nextId++);

        List<RecurringPayment> bills = new ArrayList<>();
        bills.add(ended);
        bills.add(paid);
        bills.add(bill("Later", 25));
        bills.add(bill("Soon", 15));
        bills.add(bill("Late", 8));

        List<RecurringPayment> sorted = Bills.sortByUrgency(bills, ledger(paid(paid, OCTOBER)), OCTOBER, TODAY);
        assertEquals("Late", sorted.get(0).getName());
        assertEquals("Soon", sorted.get(1).getName());
        assertEquals("Later", sorted.get(2).getName());
        assertEquals("Paid", sorted.get(3).getName());
        assertEquals("Ended", sorted.get(4).getName());
    }

    @Test
    public void periodsCountMonthsAcrossYears() {
        assertEquals(202701, Periods.plus(202612, 1));
        assertEquals(202512, Periods.plus(202601, -1));
        assertEquals(202611, Periods.plus(202511, 12));
        assertEquals(14, Periods.between(202511, 202701));
        assertEquals(OCTOBER, Periods.of(TODAY));
    }
}
