package com.example.expensemate.ui.loans;

import com.example.expensemate.data.LoanSummary;
import com.example.expensemate.util.Money;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** How a loan's progress reads in lists and sheets. */
public final class LoanText {
    private LoanText() {
    }

    public static String date(Date date) {
        return date == null ? "" : new SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(date);
    }

    public static String statusLine(LoanSummary summary) {
        String lent = "lent " + date(summary.loan.getDate());
        if (summary.isCompleted()) {
            Date completed = summary.completedDate();
            String when = completed != null ? "Returned " + date(completed) : "Returned";
            if (!summary.isFullyReturned()) {
                when += ", " + Money.format(summary.returned) + " of " + Money.format(summary.loan.getAmount());
            }
            return when + " · " + lent;
        }
        if (summary.returned > 0) {
            return Money.format(summary.returned) + " of " + Money.format(summary.loan.getAmount()) + " back · " + lent;
        }
        return capitalise(lent);
    }

    public static int progress(LoanSummary summary) {
        double amount = summary.loan.getAmount();
        return amount > 0 ? (int) Math.min(100, Math.round(summary.returned * 100 / amount)) : 100;
    }

    /** What an open loan is listed as when picking which one a return pays back. */
    public static String pickerLine(LoanSummary summary) {
        return summary.loan.getPerson() + " · " + Money.format(summary.outstanding()) + " to get back";
    }

    private static String capitalise(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
