package com.example.expensemate.ui.accounts;

import android.view.LayoutInflater;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.expensemate.R;
import com.example.expensemate.data.Account;
import com.example.expensemate.databinding.DialogAccountFormBinding;
import com.example.expensemate.ui.common.FormSheet;
import com.example.expensemate.ui.common.Pickers;
import com.example.expensemate.ui.common.Ui;
import com.example.expensemate.viewmodel.AccountViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Everything about changing an account, shared by the list and the details screen. */
public final class AccountForm {
    private AccountForm() {
    }

    public static void show(FragmentActivity activity, @Nullable Account account) {
        show(activity, account, account != null && account.isReal());
    }

    /** For a new account, real picks which type the form starts on. */
    public static void show(FragmentActivity activity, @Nullable Account account, boolean real) {
        DialogAccountFormBinding form = DialogAccountFormBinding.inflate(LayoutInflater.from(activity));
        SimpleDateFormat format = new SimpleDateFormat("MMM yyyy", Locale.getDefault());
        Calendar expiry = Calendar.getInstance();
        final boolean[] hasExpiry = { false };

        form.typeToggle.check(real ? R.id.typeReal : R.id.typeVirtual);
        // Transactions already point at this account as one kind or the other
        form.typeVirtual.setEnabled(account == null);
        form.typeReal.setEnabled(account == null);
        if (account != null) {
            form.etName.setText(account.getName());
            form.etBank.setText(account.getBank());
            form.etNumber.setText(account.getAccountNumber());
            form.etDescription.setText(account.getDescription());
            if (account.getExpiryDate() != null) {
                expiry.setTime(account.getExpiryDate());
                hasExpiry[0] = true;
                form.etExpiry.setText(format.format(expiry.getTime()));
            }
        }
        form.etExpiry.setOnClickListener(v -> Pickers.pickDate(activity.getSupportFragmentManager(), "Card expiry",
                expiry, () -> {
                    hasExpiry[0] = true;
                    form.etExpiry.setText(format.format(expiry.getTime()));
                }));

        FormSheet sheet = FormSheet.create(activity,
                account == null ? "Add an account" : "Edit " + account.getName(), form.getRoot());
        sheet.onSave(() -> {
            String name = form.etName.getText().toString().trim();
            form.tilName.setError(name.isEmpty() ? "Enter a name" : null);
            if (name.isEmpty()) {
                return;
            }
            String bank = form.etBank.getText().toString().trim();
            String number = form.etNumber.getText().toString().trim();
            String description = form.etDescription.getText().toString().trim();
            Date expiryDate = hasExpiry[0] ? expiry.getTime() : null;

            AccountViewModel viewModel = new ViewModelProvider(activity).get(AccountViewModel.class);
            if (account == null) {
                Account added = new Account(name, number, bank, expiryDate, description);
                added.setType(form.typeToggle.getCheckedButtonId() == R.id.typeReal ? Account.TYPE_REAL : Account.TYPE_VIRTUAL);
                viewModel.insert(added);
            } else {
                account.setName(name);
                account.setBank(bank);
                account.setAccountNumber(number);
                account.setDescription(description);
                account.setExpiryDate(expiryDate);
                viewModel.update(account);
            }
            sheet.dismiss();
        });
        sheet.show();
    }

    /** Shows only the last four digits, so a full number is never on screen by accident. */
    public static String detailLine(Account account) {
        StringBuilder detail = new StringBuilder();
        if (notBlank(account.getBank())) {
            detail.append(account.getBank());
        }
        String number = account.getAccountNumber();
        if (notBlank(number)) {
            String last = number.length() > 4 ? number.substring(number.length() - 4) : number;
            if (detail.length() > 0) {
                detail.append(' ');
            }
            detail.append("••").append(last);
        }
        if (account.getExpiryDate() != null) {
            if (detail.length() > 0) {
                detail.append(" · ");
            }
            detail.append("Expires ").append(new SimpleDateFormat("MM/yy", Locale.getDefault()).format(account.getExpiryDate()));
        }
        if (detail.length() == 0 && notBlank(account.getDescription())) {
            detail.append(account.getDescription());
        }
        return detail.toString();
    }

    public static String typeLabel(Account account) {
        return account.isReal() ? "real" : "virtual";
    }

    private static boolean notBlank(String text) {
        return text != null && !text.trim().isEmpty();
    }

    public static void confirmDelete(FragmentActivity activity, Account account, Runnable onDeleted) {
        if (account.isDefault()) {
            Ui.snackbar(activity, "Choose another default " + typeLabel(account) + " account before deleting this one");
            return;
        }
        new MaterialAlertDialogBuilder(activity)
                .setTitle("Delete " + account.getName() + "?")
                .setMessage("Its transactions move to your default " + typeLabel(account) + " account.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    new ViewModelProvider(activity).get(AccountViewModel.class).delete(account);
                    Ui.snackbar(activity, account.getName() + " deleted");
                    if (onDeleted != null) {
                        onDeleted.run();
                    }
                })
                .show();
    }
}
