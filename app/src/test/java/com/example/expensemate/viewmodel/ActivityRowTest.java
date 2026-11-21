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
        assertEquals(-1240, built.get(0).dayNet.get("INR"), 0.001);
        assertEquals("Yesterday", built.get(4).dayLabel);
        assertEquals(85000, built.get(4).dayNet.get("INR"), 0.001);
        assertEquals("9 October", built.get(6).dayLabel);
        assertEquals(-450, built.get(6).dayNet.get("INR"), 0.001);
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
    public void namesTheOtherSideWhenAFundFilterHidesIt() {
        Transaction[] transfer = Transfers.newPair(5000, 1, 2, at(12, 10), "");
        // Filtered to fund 2, so only the credit side is in the list
        List<ActivityRow> built = ActivityRow.build(Collections.singletonList(transfer[1]), Arrays.asList(transfer), NOW);

        assertEquals(2, built.size());
        assertSame(transfer[0], built.get(1).debitLeg);
        assertSame(transfer[1], built.get(1).creditLeg);
    }

    private static Transaction[] pair(long id, long fromFund, long toFund, long fromAccount, long toAccount, Date date) {
        Transaction[] legs = Transfers.newPair(100, fromFund, toFund, date, "");
        legs[0].setId(id);
        legs[1].setId(id + 1);
        legs[0].setAccountId(fromAccount);
        legs[1].setAccountId(toAccount);
        return legs;
    }

    @Test
    public void tellsAMoveFromATransferAndKeepsOnlyOneKind() {
        Transaction[] move = pair(10, 1, 2, 7, 7, at(12, 10));
        Transaction[] transfer = pair(20, 1, 1, 7, 8, at(11, 10));
        Transaction spend = row(30, 50, "DEBIT", at(12, 9));
        List<Transaction> legs = Arrays.asList(move[0], move[1], transfer[0], transfer[1]);
        List<Transaction> rows = Arrays.asList(move[0], move[1], spend, transfer[0], transfer[1]);
        List<ActivityRow> built = ActivityRow.build(rows, legs, NOW);

        assertTrue(built.get(1).isMove());
        assertFalse(built.get(1).isAccountTransfer());
        assertFalse(built.get(4).isMove());
        assertTrue(built.get(4).isAccountTransfer());
        assertFalse(built.get(2).isMove());

        // Only moves: yesterday held just the transfer, so its heading goes too
        List<ActivityRow> moves = ActivityRow.keep(built, ActivityRow::isMove);
        assertEquals(2, moves.size());
        assertEquals("Today", moves.get(0).dayLabel);
        assertTrue(moves.get(1).isMove());

        List<ActivityRow> transfers = ActivityRow.keep(built, ActivityRow::isAccountTransfer);
        assertEquals(2, transfers.size());
        assertEquals("Yesterday", transfers.get(0).dayLabel);
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
