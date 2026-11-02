package com.example.expensemate.ui.loans;

import android.text.InputType;
import android.widget.FrameLayout;

import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.expensemate.data.LoanSummary;
import com.example.expensemate.data.Transaction;
import com.example.expensemate.ui.common.Ui;
import com.example.expensemate.util.Money;
import com.example.expensemate.viewmodel.LoanViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;

/** Linking returns to loans and recording returns by hand. */
public final class LoanActions {
    private LoanActions() {
    }

    public static List<LoanSummary> open(List<LoanSummary> loans) {
        List<LoanSummary> open = new ArrayList<>();
        for (LoanSummary summary : loans) {
            if (!summary.isCompleted()) {
                open.add(summary);
            }
        }
        return open;
    }

    /** Asks which open loan a credit pays back, and links it. */
    public static void pickLoanForReturn(FragmentActivity activity, Transaction credit, List<LoanSummary> loans) {
        List<LoanSummary> open = open(loans);
        if (open.isEmpty()) {
            Ui.snackbar(activity, "No money lent is waiting to come back");
            return;
        }
        String[] lines = new String[open.size()];
        for (int i = 0; i < open.size(); i++) {
            lines[i] = LoanText.pickerLine(open.get(i));
        }
        new MaterialAlertDialogBuilder(activity)
                .setTitle("Which loan does this pay back?")
                .setItems(lines, (dialog, which) -> {
                    LoanSummary picked = open.get(which);
                    new ViewModelProvider(activity).get(LoanViewModel.class).link(credit.getId(), picked.loan.getId());
                    Ui.snackbar(activity, Money.format(credit.getAmount()) + " back from " + picked.loan.getPerson());
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** A return that never showed up as a transaction, such as cash in hand. */
    public static void recordReturn(FragmentActivity activity, LoanSummary summary) {
        TextInputLayout layout = new TextInputLayout(activity);
        layout.setHint("Amount returned");
        TextInputEditText input = new TextInputEditText(layout.getContext());
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setText(Money.plain(summary.outstanding()));
        layout.addView(input);
        FrameLayout container = new FrameLayout(activity);
        int padding = Math.round(20 * activity.getResources().getDisplayMetrics().density);
        container.setPadding(padding, padding / 2, padding, 0);
        container.addView(layout);

        new MaterialAlertDialogBuilder(activity)
                .setTitle("Record a return from " + summary.loan.getPerson())
                .setMessage("For money that came back outside your accounts, such as cash.")
                .setView(container)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (dialog, which) -> {
                    Double amount = Money.parse(input.getText() == null ? null : input.getText().toString());
                    if (amount == null) {
                        Ui.snackbar(activity, "Enter an amount above zero");
                        return;
                    }
                    new ViewModelProvider(activity).get(LoanViewModel.class).recordReturn(summary.loan, amount);
                    Ui.snackbar(activity, Money.format(amount) + " back from " + summary.loan.getPerson());
                })
                .show();
    }
}
