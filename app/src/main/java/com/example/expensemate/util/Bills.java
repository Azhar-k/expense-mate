package com.example.expensemate.util;

import com.example.expensemate.data.RecurringPayment;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Works out where a recurring payment stands today. A payment counts as paid only if it was
 * completed during the current month, so every bill starts unpaid again when a new month begins.
 */
public final class Bills {
    public enum Status { PAID, LATE, DUE_SOON, UPCOMING, ENDED }

    public static final int DUE_SOON_DAYS = 7;

    private Bills() {
    }

    public static boolean isTransfer(RecurringPayment payment) {
        return payment.getFromAccountId() != null && payment.getToAccountId() != null;
    }

    public static boolean isPaid(RecurringPayment payment, Calendar today) {
        Date last = payment.getLastCompletedDate();
        if (!payment.isCompleted() || last == null) {
            return false;
        }
        Calendar paid = (Calendar) today.clone();
        paid.setTime(last);
        return paid.get(Calendar.YEAR) == today.get(Calendar.YEAR)
                && paid.get(Calendar.MONTH) == today.get(Calendar.MONTH);
    }

    public static boolean hasEnded(RecurringPayment payment, Calendar today) {
        Date expiry = payment.getExpiryDate();
        return expiry != null && expiry.before(startOfDay(today).getTime());
    }

    /** The due day in this month, moved to the last day when the month is too short. */
    public static Calendar dueDate(RecurringPayment payment, Calendar today) {
        Calendar due = startOfDay(today);
        due.set(Calendar.DAY_OF_MONTH, 1);
        due.set(Calendar.DAY_OF_MONTH, Math.min(payment.getDueDay(), due.getActualMaximum(Calendar.DAY_OF_MONTH)));
        return due;
    }

    /** Negative when the due date has passed. */
    public static int daysUntilDue(RecurringPayment payment, Calendar today) {
        return dueDate(payment, today).get(Calendar.DAY_OF_MONTH) - today.get(Calendar.DAY_OF_MONTH);
    }

    public static Status statusOf(RecurringPayment payment, Calendar today) {
        if (hasEnded(payment, today)) {
            return Status.ENDED;
        }
        if (isPaid(payment, today)) {
            return Status.PAID;
        }
        int days = daysUntilDue(payment, today);
        if (days < 0) {
            return Status.LATE;
        }
        return days <= DUE_SOON_DAYS ? Status.DUE_SOON : Status.UPCOMING;
    }

    /** Most urgent first: late, then by due date, with paid and ended items last. */
    public static List<RecurringPayment> sortByUrgency(List<RecurringPayment> payments, Calendar today) {
        List<RecurringPayment> sorted = new ArrayList<>(payments);
        Collections.sort(sorted, (a, b) -> {
            int byGroup = Integer.compare(group(statusOf(a, today)), group(statusOf(b, today)));
            return byGroup != 0 ? byGroup : Integer.compare(a.getDueDay(), b.getDueDay());
        });
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
                return 2;
            default:
                return 3;
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
