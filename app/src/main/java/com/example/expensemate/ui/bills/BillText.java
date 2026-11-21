package com.example.expensemate.ui.bills;

import android.view.View;
import android.widget.TextView;

import com.example.expensemate.R;
import com.example.expensemate.data.RecurringPayment;
import com.example.expensemate.ui.common.Ui;
import com.example.expensemate.util.BillLedger;
import com.example.expensemate.util.Bills;
import com.example.expensemate.util.Periods;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Status words and colours for recurring payments. */
public final class BillText {
    private BillText() {
    }

    public static void styleStatusTag(TextView tag, RecurringPayment bill, BillLedger ledger, int period,
            Calendar today) {
        int days = Bills.daysUntilDue(bill, period, today);
        switch (Bills.statusOf(bill, ledger, period, today)) {
            case LATE:
                Ui.styleTag(tag, -days == 1 ? "1 day late" : -days + " days late",
                        R.color.alert_container, R.color.alert);
                break;
            case DUE_SOON:
                Ui.styleTag(tag, days == 0 ? "Today" : days == 1 ? "Tomorrow" : "In " + days + " days",
                        R.color.warning_container, R.color.warning);
                break;
            case UPCOMING:
                Ui.styleTag(tag, "In " + days + " days", R.color.surface_variant, R.color.on_surface_variant);
                break;
            default:
                tag.setVisibility(View.GONE);
                break;
        }
    }

    /** A line such as "Due 15 Oct · in 3 days", "Paid 1 Oct" or "Missed · was due 15 Sep". */
    public static String statusLine(RecurringPayment bill, BillLedger ledger, int period, Calendar today) {
        SimpleDateFormat dayMonth = new SimpleDateFormat("d MMM", Locale.getDefault());
        String due = dayMonth.format(Bills.dueDate(bill, period).getTime());
        int days = Bills.daysUntilDue(bill, period, today);
        switch (Bills.statusOf(bill, ledger, period, today)) {
            case PAID:
                Date paidOn = Bills.paidOn(bill, ledger, period);
                String done = Bills.isTransfer(bill) ? "Topped up" : "Paid";
                return paidOn == null ? done : done + " " + dayMonth.format(paidOn);
            case SKIPPED:
                return "Skipped";
            case LATE:
                return period < Periods.of(today) ? "Missed · was due " + due : "Was due " + due;
            case DUE_SOON:
                return "Due " + due + " · " + (days == 0 ? "today" : days == 1 ? "tomorrow" : "in " + days + " days");
            case ENDED:
                return "Ended " + dayMonth.format(bill.getExpiryDate());
            case NOT_STARTED:
                return "Starts " + Periods.name(bill.getStartPeriod());
            default:
                return "Due " + due;
        }
    }
}
