package com.example.expensemate.viewmodel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.example.expensemate.data.Transaction;
import com.example.expensemate.data.Transfers;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

public class ActivityRowTest {
    private static final Calendar NOW = new GregorianCalendar(2026, Calendar.OCTOBER, 12, 18, 0);

    private static Date at(int day, int hour) {
        return new GregorianCalendar(2026, Calendar.OCTOBER, day, hour, 0).getTime();
    }

    private static Transaction row(long id, double amount, String type, Date date) {
        Transaction transaction = new Transaction();
        transaction.setId(id);
        transaction.setAmount(amount);
        transaction.setTransactionType(type);
        transaction.setDate(date);
        return transaction;
    }

    @Test
    public void groupsByDayWithANetThatSkipsTransfersAndExcludedRows() {
        Transaction excluded = row(3, 999, "DEBIT", at(12, 9));
        excluded.setExcludedFromSummary(true);
        Transaction[] transfer = Transfers.newPair(5000, 1, 2, at(12, 10), "");
        transfer[0].setId(4);
        transfer[1].setId(5);
        List<Transaction> rows = Arrays.asList(
                row(1, 1240, "DEBIT", at(12, 17)), transfer[0], transfer[1], excluded,
                row(2, 85000, "CREDIT", at(11, 9)), row(6, 450, "DEBIT", at(9, 20)));

        List<ActivityRow> built = ActivityRow.build(rows, Arrays.asList(transfer), NOW);

        assertEquals(8, built.size());
        assertTrue(built.get(0).isHeader());
        assertEquals("Today", built.get(0).dayLabel);
        assertEquals(-1240, built.get(0).dayNet, 0.001);
        assertEquals("Yesterday", built.get(4).dayLabel);
        assertEquals(85000, built.get(4).dayNet, 0.001);
        assertEquals("9 October", built.get(6).dayLabel);
        assertEquals(-450, built.get(6).dayNet, 0.001);
    }

    @Test
    public void showsATransferAsOneRowWithBothSides() {
        Transaction[] transfer = Transfers.newPair(5000, 1, 2, at(12, 10), "Savings");
        List<ActivityRow> built = ActivityRow.build(Arrays.asList(transfer), Arrays.asList(transfer), NOW);

        assertEquals(2, built.size());
        ActivityRow row = built.get(1);
        assertTrue(row.isTransfer());
        assertSame(transfer[0], row.debitLeg);
        assertSame(transfer[1], row.creditLeg);
    }

    @Test
    public void namesTheOtherSideWhenAnAccountFilterHidesIt() {
        Transaction[] transfer = Transfers.newPair(5000, 1, 2, at(12, 10), "");
        // Filtered to account 2, so only the credit side is in the list
        List<ActivityRow> built = ActivityRow.build(Collections.singletonList(transfer[1]), Arrays.asList(transfer), NOW);

        assertEquals(2, built.size());
        assertSame(transfer[0], built.get(1).debitLeg);
        assertSame(transfer[1], built.get(1).creditLeg);
    }

    @Test
    public void emptyListHasNoHeaders() {
        assertTrue(ActivityRow.build(new ArrayList<>(), new ArrayList<>(), NOW).isEmpty());
        assertTrue(ActivityRow.build(null, null, NOW).isEmpty());
    }

    @Test
    public void includesTheYearForAnOlderDay() {
        Transaction old = row(1, 10, "DEBIT", new GregorianCalendar(2025, Calendar.MARCH, 3, 12, 0).getTime());
        assertEquals("3 March 2025",
                ActivityRow.build(Collections.singletonList(old), null, NOW).get(0).dayLabel);
    }

    @Test
    public void rowContentChangesAreSeen() {
        Transaction before = row(1, 10, "DEBIT", at(12, 9));
        Transaction after = before.copy();
        after.setCategory("Food");
        ActivityRow a = ActivityRow.build(Collections.singletonList(before), null, NOW).get(1);
        ActivityRow b = ActivityRow.build(Collections.singletonList(after), null, NOW).get(1);
        assertTrue(a.sameItem(b));
        assertFalse(a.sameContent(b));
    }
}
