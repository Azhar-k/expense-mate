package com.example.expensemate.ui.bills;

import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.expensemate.data.Account;
import com.example.expensemate.data.RecurringPayment;
import com.example.expensemate.ui.common.TransactionText;
import com.example.expensemate.ui.common.Ui;
import com.example.expensemate.util.Money;
import com.example.expensemate.viewmodel.RecurringPaymentsViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Moving money for planned transfers, one at a time or all together. */
public final class BillActions {
    private BillActions() {
    }

    public static Map<Long, String> names(List<Account> accounts) {
        Map<Long, String> names = new HashMap<>();
        for (Account account : accounts) {
            names.put(account.getId(), account.getName());
        }
        return names;
    }

    public static void confirmMove(FragmentActivity activity, RecurringPayment bill, Map<Long, String> names) {
        new MaterialAlertDialogBuilder(activity)
                .setTitle("Move " + Money.format(bill.getAmount()) + "?")
                .setMessage("From " + TransactionText.accountName(names, bill.getFromAccountId()) + " to "
                        + TransactionText.accountName(names, bill.getToAccountId()) + " for " + bill.getName() + ".")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Move", (dialog, which) -> {
                    move(activity, bill);
                    Ui.snackbar(activity, "Moved " + Money.format(bill.getAmount()));
                })
                .show();
    }

    public static void confirmMoveAll(FragmentActivity activity, List<RecurringPayment> pending,
            Map<Long, String> names) {
        StringBuilder message = new StringBuilder();
        double total = 0;
        for (RecurringPayment bill : pending) {
            message.append(bill.getName()).append(" (").append(Money.format(bill.getAmount())).append("): ")
                    .append(TransactionText.accountName(names, bill.getFromAccountId())).append(" → ")
                    .append(TransactionText.accountName(names, bill.getToAccountId())).append('\n');
            total += bill.getAmount();
        }
        double finalTotal = total;
        new MaterialAlertDialogBuilder(activity)
                .setTitle("Move " + Money.format(total) + "?")
                .setMessage(message.toString().trim())
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Move all", (dialog, which) -> {
                    for (RecurringPayment bill : pending) {
                        move(activity, bill);
                    }
                    Ui.snackbar(activity, "Moved " + Money.format(finalTotal));
                })
                .show();
    }

    private static void move(FragmentActivity activity, RecurringPayment bill) {
        new ViewModelProvider(activity).get(RecurringPaymentsViewModel.class).payByTransfer(bill);
    }
}
