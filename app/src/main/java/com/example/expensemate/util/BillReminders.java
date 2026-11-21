package com.example.expensemate.util;

import com.example.expensemate.data.RecurringPayment;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Which bills to remind about on a given day: one still owed that is due in {@code daysAhead} days,
 * due today, or was due yesterday. Fund top-ups are moves of your own money, so they get none.
 */
public final class BillReminders {
    public enum Kind { AHEAD, TODAY, OVERDUE }

    private BillReminders() {
    }

    public static final class Reminder {
        public final RecurringPayment bill;
        public final int period;
        public final Kind kind;
        public final int days; // Until the due date; negative once it has passed

        Reminder(RecurringPayment bill, int period, Kind kind, int days) {
            this.bill = bill;
            this.period = period;
            this.kind = kind;
            this.days = days;
        }

        /** Tells one reminder from another, so the same one is not sent twice. */
        public String key() {
            return bill.getId() + ":" + period + ":" + kind;
        }
    }

    public static List<Reminder> on(List<RecurringPayment> bills, BillLedger ledger, Calendar today, int daysAhead) {
        List<Reminder> reminders = new ArrayList<>();
        int current = Periods.of(today);
        for (RecurringPayment bill : bills) {
            if (Bills.isTransfer(bill)) {
                continue;
            }
            // A due date a few days off can sit in next month, and yesterday's in last month
            for (int period = Periods.plus(current, -1); period <= Periods.plus(current, 1);
                    period = Periods.plus(period, 1)) {
                if (!Bills.isOwed(Bills.statusOf(bill, ledger, period, today))) {
                    continue;
                }
                int days = Bills.daysUntilDue(bill, period, today);
                Kind kind = days == 0 ? Kind.TODAY : days == -1 ? Kind.OVERDUE
                        : daysAhead > 0 && days == daysAhead ? Kind.AHEAD : null;
                if (kind != null) {
                    reminders.add(new Reminder(bill, period, kind, days));
                }
            }
        }
        return reminders;
    }

    /** "Rent is due in 3 days", "Rent is due today" or "Rent was due yesterday". */
    public static String title(Reminder reminder) {
        String name = reminder.bill.getName();
        switch (reminder.kind) {
            case TODAY:
                return name + " is due today";
            case OVERDUE:
                return name + " was due yesterday";
            default:
                return name + " is due " + (reminder.days == 1 ? "tomorrow" : "in " + reminder.days + " days");
        }
    }
}
