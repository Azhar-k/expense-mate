package com.example.expensemate.util;

import androidx.annotation.Nullable;

import com.example.expensemate.data.BillPayment;
import com.example.expensemate.data.RecurringPayment;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Works out where a recurring payment stands in a given month. Each month is paid or skipped on its
 * own, so a month left unpaid stays missed after it is over instead of starting fresh.
 */
public final class Bills {
    public enum Status { PAID, SKIPPED, LATE, DUE_SOON, UPCOMING, ENDED, NOT_STARTED }

    public static final int DUE_SOON_DAYS = 7;
    /** How far back an unpaid month still counts as owed. */
    public static final int LOOK_BACK_MONTHS = 12;

    private static final long DAY_MS = 24L * 60 * 60 * 1000;

    private Bills() {
    }

    /** A bill's month that went by without being paid or skipped. */
    public static final class Missed {
        public final RecurringPayment bill;
        public final int period;

        Missed(RecurringPayment bill, int period) {
            this.bill = bill;
            this.period = period;
        }
    }

    public static boolean isTransfer(RecurringPayment payment) {
        return payment.getFromFundId() != null && payment.getToFundId() != null;
    }

    public static boolean isDone(Status status) {
        return status == Status.PAID || status == Status.SKIPPED;
    }

    /** The due day in that month, moved to the last day when the month is too short. */
    public static Calendar dueDate(RecurringPayment payment, int period) {
        Calendar due = Periods.firstDay(period);
        due.set(Calendar.DAY_OF_MONTH, Math.min(payment.getDueDay(), due.getActualMaximum(Calendar.DAY_OF_MONTH)));
        return due;
    }

    /** Negative when the due date has passed. */
    public static int daysUntilDue(RecurringPayment payment, int period, Calendar today) {
        long diff = dueDate(payment, period).getTimeInMillis() - startOfDay(today).getTimeInMillis();
        return (int) Math.round(diff / (double) DAY_MS);
    }

    /** Ended before that month's due date, so nothing is owed then. */
    public static boolean hasEnded(RecurringPayment payment, int period) {
        Date expiry = payment.getExpiryDate();
        return expiry != null && expiry.before(dueDate(payment, period).getTime());
    }

    public static boolean hasStarted(RecurringPayment payment, int period) {
        return payment.getStartPeriod() <= period;
    }

    public static Status statusOf(RecurringPayment payment, BillLedger ledger, int period, Calendar today) {
        BillPayment row = ledger.get(payment.getId(), period);
        if (row != null) {
            return row.isSkipped() ? Status.SKIPPED : Status.PAID;
        }
        if (hasEnded(payment, period)) {
            return Status.ENDED;
        }
        if (!hasStarted(payment, period)) {
            return Status.NOT_STARTED;
        }
        int days = daysUntilDue(payment, period, today);
        if (days < 0) {
            return Status.LATE;
        }
        return days <= DUE_SOON_DAYS ? Status.DUE_SOON : Status.UPCOMING;
    }

    @Nullable
    public static Date paidOn(RecurringPayment payment, BillLedger ledger, int period) {
        BillPayment row = ledger.get(payment.getId(), period);
        return row == null ? null : row.getPaidOn();
    }

    /** Fund top-ups still to be made in that month. */
    public static List<RecurringPayment> pendingTransfers(List<RecurringPayment> bills, BillLedger ledger, int period,
            Calendar today) {
        List<RecurringPayment> pending = new ArrayList<>();
        for (RecurringPayment bill : bills) {
            Status status = statusOf(bill, ledger, period, today);
            if (isTransfer(bill) && isOwed(status) && !bill.getFromFundId().equals(bill.getToFundId())) {
                pending.add(bill);
            }
        }
        return pending;
    }

    /** Owed in that month and not yet paid or skipped. */
    public static boolean isOwed(Status status) {
        return status == Status.LATE || status == Status.DUE_SOON || status == Status.UPCOMING;
    }

    /** Months before {@code period}, back to {@value #LOOK_BACK_MONTHS}, that were missed. Newest first. */
    public static List<Missed> missedBefore(List<RecurringPayment> bills, BillLedger ledger, int period,
            Calendar today) {
        List<Missed> missed = new ArrayList<>();
        for (int back = 1; back <= LOOK_BACK_MONTHS; back++) {
            int month = Periods.plus(period, -back);
            for (RecurringPayment bill : sortByDueDay(bills)) {
                if (statusOf(bill, ledger, month, today) == Status.LATE) {
                    missed.add(new Missed(bill, month));
                }
            }
        }
        return missed;
    }

    /**
     * The month a payment made on {@code day} most likely pays: its own month, or when that is
     * already done, the oldest one owed before it or the next one. 0 when none is owed.
     */
    public static int periodToPay(RecurringPayment bill, BillLedger ledger, Calendar day) {
        int own = Periods.of(day);
        if (isOwed(statusOf(bill, ledger, own, day))) {
            return own;
        }
        for (int back = LOOK_BACK_MONTHS; back >= 1; back--) {
            int month = Periods.plus(own, -back);
            if (isOwed(statusOf(bill, ledger, month, day))) {
                return month;
            }
        }
        int next = Periods.plus(own, 1);
        return isOwed(statusOf(bill, ledger, next, day)) ? next : 0;
    }

    /** Most urgent first: late, then by due date, with done, ended and not started ones last. */
    public static List<RecurringPayment> sortByUrgency(List<RecurringPayment> payments, BillLedger ledger, int period,
            Calendar today) {
        List<RecurringPayment> sorted = new ArrayList<>(payments);
        Collections.sort(sorted, (a, b) -> {
            int byGroup = Integer.compare(group(statusOf(a, ledger, period, today)),
                    group(statusOf(b, ledger, period, today)));
            return byGroup != 0 ? byGroup : Integer.compare(a.getDueDay(), b.getDueDay());
        });
        return sorted;
    }

    private static List<RecurringPayment> sortByDueDay(List<RecurringPayment> bills) {
        List<RecurringPayment> sorted = new ArrayList<>(bills);
        Collections.sort(sorted, (a, b) -> Integer.compare(a.getDueDay(), b.getDueDay()));
        return sorted;
    }

    private static int group(Status status) {
        switch (status) {
            case LATE:
                return 0;
            case DUE_SOON:
            case UPCOMING:
                return 1;
            case PAID:
            case SKIPPED:
                return 2;
            case ENDED:
                return 3;
            default:
                return 4;
        }
    }

    private static Calendar startOfDay(Calendar day) {
        Calendar start = (Calendar) day.clone();
        start.set(Calendar.HOUR_OF_DAY, 0);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);
        return start;
    }
}
