package com.example.expensemate.ui.bills;

import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.expensemate.data.Fund;
import com.example.expensemate.data.RecurringPayment;
import com.example.expensemate.ui.common.TransactionText;
import com.example.expensemate.ui.common.Ui;
import com.example.expensemate.util.CurrencyTotals;
import com.example.expensemate.util.Money;
import com.example.expensemate.viewmodel.RecurringPaymentsViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Fund top-ups, one at a time or all together. */
public final class BillActions {
    private BillActions() {
    }

    public static Map<Long, String> names(List<Fund> funds) {
        Map<Long, String> names = new HashMap<>();
        for (Fund fund : funds) {
            names.put(fund.getId(), fund.getName());
        }
        return names;
    }

    public static void confirmMove(FragmentActivity activity, RecurringPayment bill, Map<Long, String> names) {
        new MaterialAlertDialogBuilder(activity)
                .setTitle("Top up " + TransactionText.fundName(names, bill.getToFundId()) + " with "
                        + Money.format(bill.getAmount(), bill.getCurrency()) + "?")
                .setMessage("From " + TransactionText.fundName(names, bill.getFromFundId()) + " to "
                        + TransactionText.fundName(names, bill.getToFundId()) + " for " + bill.getName() + ".")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Top up", (dialog, which) -> {
                    move(activity, bill);
                    Ui.snackbar(activity, TransactionText.fundName(names, bill.getToFundId()) + " topped up with "
                            + Money.format(bill.getAmount(), bill.getCurrency()));
                })
                .show();
    }

    public static void confirmMoveAll(FragmentActivity activity, List<RecurringPayment> pending,
            Map<Long, String> names) {
        StringBuilder message = new StringBuilder();
        CurrencyTotals total = new CurrencyTotals();
        for (RecurringPayment bill : pending) {
            message.append(bill.getName()).append(" (").append(Money.format(bill.getAmount(), bill.getCurrency())).append("): ")
                    .append(TransactionText.fundName(names, bill.getFromFundId())).append(" → ")
                    .append(TransactionText.fundName(names, bill.getToFundId())).append('\n');
            total.add(bill.getCurrency(), bill.getAmount());
        }
        String totalText = total.format(pending.get(0).getCurrency(), pending.get(0).getCurrency());
        new MaterialAlertDialogBuilder(activity)
                .setTitle("Top up funds with " + totalText + "?")
                .setMessage(message.toString().trim())
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Top up all", (dialog, which) -> {
                    for (RecurringPayment bill : pending) {
                        move(activity, bill);
                    }
                    Ui.snackbar(activity, "Funds topped up with " + totalText);
                })
                .show();
    }

    private static void move(FragmentActivity activity, RecurringPayment bill) {
        new ViewModelProvider(activity).get(RecurringPaymentsViewModel.class).payByTransfer(bill);
    }
}
