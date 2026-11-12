package com.example.expensemate.viewmodel;

import com.example.expensemate.data.Transaction;
import com.example.expensemate.util.CurrencyTotals;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * One line in a transaction list: a day heading, a transaction, or a transfer shown as a
 * single row even though it is stored as a debit and a credit.
 */
public class ActivityRow {
    public final String dayLabel;
    public final CurrencyTotals dayNet; // One net per currency the day has
    public final Transaction transaction;
    public final Transaction debitLeg;
    public final Transaction creditLeg;

    private ActivityRow(String dayLabel, CurrencyTotals dayNet, Transaction transaction, Transaction debitLeg,
            Transaction creditLeg) {
        this.dayLabel = dayLabel;
        this.dayNet = dayNet;
        this.transaction = transaction;
        this.debitLeg = debitLeg;
        this.creditLeg = creditLeg;
    }

    public boolean isHeader() {
        return transaction == null;
    }

    public boolean isTransfer() {
        return transaction != null && transaction.isTransfer();
    }

    public boolean sameItem(ActivityRow other) {
        if (isHeader() || other.isHeader()) {
            return isHeader() && other.isHeader() && dayLabel.equals(other.dayLabel);
        }
        return isTransfer() && other.isTransfer()
                ? transaction.getTransferGroupId().equals(other.transaction.getTransferGroupId())
                : transaction.getId() == other.transaction.getId();
    }

    public boolean sameContent(ActivityRow other) {
        if (isHeader() || other.isHeader()) {
            return isHeader() && other.isHeader() && dayNet.equals(other.dayNet);
        }
        return sameTransaction(transaction, other.transaction) && sameTransaction(debitLeg, other.debitLeg)
                && sameTransaction(creditLeg, other.creditLeg);
    }

    private static boolean sameTransaction(Transaction a, Transaction b) {
        if (a == null || b == null) {
            return a == b;
        }
        return a.getId() == b.getId() && a.getAmount() == b.getAmount()
                && Objects.equals(a.getDate(), b.getDate())
                && Objects.equals(a.getDescription(), b.getDescription())
                && Objects.equals(a.getReceiverName(), b.getReceiverName())
                && Objects.equals(a.getCategory(), b.getCategory())
                && Objects.equals(a.getTransactionType(), b.getTransactionType())
                && Objects.equals(a.getAccountId(), b.getAccountId())
                && Objects.equals(a.getRealAccountId(), b.getRealAccountId())
                && a.isExcludedFromSummary() == b.isExcludedFromSummary()
                && a.getCurrency().equals(b.getCurrency())
                && a.isNeedsReview() == b.isNeedsReview();
    }

    /**
     * @param transactions the rows to show, newest first
     * @param transferLegs every transfer row in the same date range, so the other side of a
     *                     transfer can be named even when an account filter hides it
     */
    public static List<ActivityRow> build(List<Transaction> transactions, List<Transaction> transferLegs,
            Calendar now) {
        List<ActivityRow> rows = new ArrayList<>();
        if (transactions == null) {
            return rows;
        }
        Map<String, Transaction> debits = new HashMap<>();
        Map<String, Transaction> credits = new HashMap<>();
        if (transferLegs != null) {
            for (Transaction leg : transferLegs) {
                ("DEBIT".equals(leg.getTransactionType()) ? debits : credits).put(leg.getTransferGroupId(), leg);
            }
        }

        Set<String> seenGroups = new HashSet<>();
        Calendar day = (Calendar) now.clone();
        Long currentDay = null;
        int headerIndex = -1;
        CurrencyTotals net = new CurrencyTotals();
        for (Transaction row : transactions) {
            String groupId = row.getTransferGroupId();
            if (groupId != null && !seenGroups.add(groupId)) {
                continue;
            }
            day.setTime(row.getDate());
            long dayKey = day.get(Calendar.YEAR) * 1000L + day.get(Calendar.DAY_OF_YEAR);
            if (currentDay == null || currentDay != dayKey) {
                closeDay(rows, headerIndex, net);
                currentDay = dayKey;
                headerIndex = rows.size();
                net = new CurrencyTotals();
                rows.add(new ActivityRow(dayLabel(day, now), net, null, null, null));
            }
            if (groupId != null) {
                rows.add(new ActivityRow(null, null, row, debits.get(groupId), credits.get(groupId)));
            } else {
                rows.add(new ActivityRow(null, null, row, null, null));
                if (!row.isExcludedFromSummary()) {
                    net.add(row.getCurrency(), "CREDIT".equals(row.getTransactionType()) ? row.getAmount() : -row.getAmount());
                }
            }
        }
        closeDay(rows, headerIndex, net);
        return rows;
    }

    private static void closeDay(List<ActivityRow> rows, int headerIndex, CurrencyTotals net) {
        if (headerIndex >= 0) {
            rows.set(headerIndex, new ActivityRow(rows.get(headerIndex).dayLabel, net, null, null, null));
        }
    }

    static String dayLabel(Calendar day, Calendar now) {
        Calendar yesterday = (Calendar) now.clone();
        yesterday.add(Calendar.DAY_OF_YEAR, -1);
        if (sameDay(day, now)) {
            return "Today";
        }
        if (sameDay(day, yesterday)) {
            return "Yesterday";
        }
        String pattern = day.get(Calendar.YEAR) == now.get(Calendar.YEAR) ? "d MMMM" : "d MMMM yyyy";
        return new SimpleDateFormat(pattern, Locale.getDefault()).format(day.getTime());
    }

    private static boolean sameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }
}
