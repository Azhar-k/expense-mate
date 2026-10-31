package com.example.expensemate.ui.bills;

import android.view.View;
import android.widget.TextView;

import com.example.expensemate.R;
import com.example.expensemate.data.RecurringPayment;
import com.example.expensemate.ui.common.Ui;
import com.example.expensemate.util.Bills;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/** Status words and colours for recurring payments. */
public final class BillText {
    private BillText() {
    }

    public static void styleStatusTag(TextView tag, RecurringPayment bill, Calendar today) {
        int days = Bills.daysUntilDue(bill, today);
        switch (Bills.statusOf(bill, today)) {
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

    /** A line such as "Due 15 Oct · in 3 days" or "Paid 1 Oct". */
    public static String statusLine(RecurringPayment bill, Calendar today) {
        SimpleDateFormat dayMonth = new SimpleDateFormat("d MMM", Locale.getDefault());
        String due = dayMonth.format(Bills.dueDate(bill, today).getTime());
        int days = Bills.daysUntilDue(bill, today);
        switch (Bills.statusOf(bill, today)) {
            case PAID:
                return (Bills.isTransfer(bill) ? "Moved " : "Paid ") + dayMonth.format(bill.getLastCompletedDate());
            case LATE:
                return "Was due " + due;
            case DUE_SOON:
                return "Due " + due + " · " + (days == 0 ? "today" : days == 1 ? "tomorrow" : "in " + days + " days");
            case ENDED:
                return "Ended " + dayMonth.format(bill.getExpiryDate());
            default:
                return "Due " + due;
        }
    }
}
