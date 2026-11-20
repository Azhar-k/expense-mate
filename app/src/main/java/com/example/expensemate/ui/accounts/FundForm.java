package com.example.expensemate.ui.accounts;

import android.view.LayoutInflater;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.expensemate.data.Fund;
import com.example.expensemate.databinding.DialogFundFormBinding;
import com.example.expensemate.ui.common.FormSheet;
import com.example.expensemate.ui.common.Pickers;
import com.example.expensemate.ui.common.Ui;
import com.example.expensemate.util.Money;
import com.example.expensemate.viewmodel.FundViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Everything about changing a fund, shared by the Money tab and the details screen. */
public final class FundForm {
    private FundForm() {
    }

    public static void show(FragmentActivity activity, @Nullable Fund fund) {
        DialogFundFormBinding form = DialogFundFormBinding.inflate(LayoutInflater.from(activity));
        SimpleDateFormat format = new SimpleDateFormat("MMM yyyy", Locale.getDefault());
        Calendar targetBy = Calendar.getInstance();
        final boolean[] hasTargetDate = { false };

        if (fund != null) {
            form.etName.setText(fund.getName());
            form.etDescription.setText(fund.getDescription());
            if (fund.getTarget() != null) {
                form.etTarget.setText(Money.plain(fund.getTarget()));
            }
            if (fund.getTargetDate() != null) {
                targetBy.setTime(fund.getTargetDate());
                hasTargetDate[0] = true;
                form.etTargetDate.setText(format.format(targetBy.getTime()));
            }
        }
        form.etTargetDate.setOnClickListener(v -> Pickers.pickDate(activity.getSupportFragmentManager(),
                "Reach the target by", targetBy, () -> {
                    hasTargetDate[0] = true;
                    form.etTargetDate.setText(format.format(targetBy.getTime()));
                }));

        FundViewModel viewModel = new ViewModelProvider(activity).get(FundViewModel.class);
        FormSheet sheet = FormSheet.create(activity, fund == null ? "Add a fund" : "Edit " + fund.getName(),
                form.getRoot());
        sheet.onSave(() -> {
            String name = form.etName.getText().toString().trim();
            form.tilName.setError(name.isEmpty() ? "Enter a name" : null);
            String targetText = form.etTarget.getText().toString().trim();
            Double target = targetText.isEmpty() ? null : Money.parse(targetText);
            boolean badTarget = !targetText.isEmpty() && (target == null || target <= 0);
            form.tilTarget.setError(badTarget ? "Enter an amount above zero" : null);
            if (name.isEmpty() || badTarget) {
                return;
            }
            String description = form.etDescription.getText().toString().trim();
            // A date means nothing without an amount to reach
            Date targetDate = target != null && hasTargetDate[0] ? targetBy.getTime() : null;

            Fund saved = fund != null ? fund : new Fund(name, description);
            saved.setName(name);
            saved.setDescription(description);
            saved.setTarget(target);
            saved.setTargetDate(targetDate);
            if (fund == null) {
                viewModel.insert(saved);
            } else {
                viewModel.update(saved);
            }
            sheet.dismiss();
        });
        sheet.show();
    }

    /** How far a fund with a target has got, such as "₹4,000 to go of ₹10,000 · by Mar 2027", or null without one. */
    @Nullable
    public static String targetLine(Fund fund, double saved, String currency) {
        if (fund.getTarget() == null) {
            return null;
        }
        double toGo = fund.getTarget() - saved;
        StringBuilder line = new StringBuilder(toGo <= 0
                ? "Target of " + Money.format(fund.getTarget(), currency) + " reached"
                : Money.format(toGo, currency) + " to go of " + Money.format(fund.getTarget(), currency));
        if (fund.getTargetDate() != null && toGo > 0) {
            line.append(" · by ").append(new SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(fund.getTargetDate()));
        }
        return line.toString();
    }

    /** From 0 to 100, how much of its target a fund holds. */
    public static int targetPercent(Fund fund, double saved) {
        if (fund.getTarget() == null || fund.getTarget() <= 0) {
            return 0;
        }
        return (int) Math.max(0, Math.min(100, Math.round(saved * 100 / fund.getTarget())));
    }

    /** What a fund without a target says under its name. */
    public static String detailLine(Fund fund) {
        String description = fund.getDescription();
        return description != null ? description.trim() : "";
    }

    public static void confirmDelete(FragmentActivity activity, Fund fund, @Nullable Runnable onDeleted) {
        if (fund.isDefault()) {
            Ui.snackbar(activity, "Make another fund your everyday fund before deleting this one");
            return;
        }
        new MaterialAlertDialogBuilder(activity)
                .setTitle("Delete " + fund.getName() + "?")
                .setMessage("Its transactions move to your everyday fund.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    new ViewModelProvider(activity).get(FundViewModel.class).delete(fund);
                    Ui.snackbar(activity, fund.getName() + " deleted");
                    if (onDeleted != null) {
                        onDeleted.run();
                    }
                })
                .show();
    }
}
