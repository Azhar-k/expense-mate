package com.example.expensemate.ui.accounts;

import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.expensemate.R;
import com.example.expensemate.data.Account;
import com.example.expensemate.databinding.DialogAccountFormBinding;
import com.example.expensemate.ui.common.CurrencyField;
import com.example.expensemate.ui.common.FormSheet;
import com.example.expensemate.ui.common.Pickers;
import com.example.expensemate.ui.common.Ui;
import com.example.expensemate.util.Currencies;
import com.example.expensemate.util.Money;
import com.example.expensemate.viewmodel.AccountViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
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
        Calendar targetBy = Calendar.getInstance();
        final boolean[] hasTargetDate = { false };

        form.typeToggle.check(real ? R.id.typeReal : R.id.typeFund);
        showFieldsFor(form, real);
        form.typeToggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                showFieldsFor(form, checkedId == R.id.typeReal);
            }
        });
        AccountViewModel viewModel = new ViewModelProvider(activity).get(AccountViewModel.class);
        Ui.observeOnce(viewModel.getAllAccounts(), activity, list -> {
            String main = Currencies.main(list);
            String current = account != null && account.getCurrency() != null ? account.getCurrency() : main;
            CurrencyField.setUp(form.etCurrency, current, main, Currencies.inUse(list));
        });
        // Transactions already point at this account as one kind or the other
        form.typeFund.setEnabled(account == null);
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
            if (account.getTarget() != null) {
                form.etTarget.setText(Money.plain(account.getTarget()));
            }
            if (account.getTargetDate() != null) {
                targetBy.setTime(account.getTargetDate());
                hasTargetDate[0] = true;
                form.etTargetDate.setText(format.format(targetBy.getTime()));
            }
        }
        form.etTargetDate.setOnClickListener(v -> Pickers.pickDate(activity.getSupportFragmentManager(),
                "Reach the target by", targetBy, () -> {
                    hasTargetDate[0] = true;
                    form.etTargetDate.setText(format.format(targetBy.getTime()));
                }));
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
            boolean isReal = form.typeToggle.getCheckedButtonId() == R.id.typeReal;
            String currency = isReal ? CurrencyField.read(form.etCurrency) : null;
            form.tilCurrency.setError(isReal && currency == null ? "Pick a currency" : null);
            String targetText = form.etTarget.getText().toString().trim();
            Double target = isReal || targetText.isEmpty() ? null : Money.parse(targetText);
            boolean badTarget = !isReal && !targetText.isEmpty() && (target == null || target <= 0);
            form.tilTarget.setError(badTarget ? "Enter an amount above zero" : null);
            if ((isReal && currency == null) || badTarget) {
                return;
            }
            // A date means nothing without an amount to reach
            Date targetDate = target != null && hasTargetDate[0] ? targetBy.getTime() : null;

            if (account == null) {
                Account added = new Account(name, number, bank, expiryDate, description);
                added.setType(isReal ? Account.TYPE_REAL : Account.TYPE_FUND);
                added.setCurrency(currency);
                added.setTarget(target);
                added.setTargetDate(targetDate);
                viewModel.insert(added);
                sheet.dismiss();
                return;
            }
            Runnable save = () -> {
                account.setName(name);
                account.setBank(bank);
                account.setAccountNumber(number);
                account.setDescription(description);
                account.setExpiryDate(expiryDate);
                account.setCurrency(currency);
                account.setTarget(target);
                account.setTargetDate(targetDate);
                viewModel.update(account);
                sheet.dismiss();
            };
            if (isReal && account.getCurrency() != null && !account.getCurrency().equals(currency)) {
                new MaterialAlertDialogBuilder(activity)
                        .setTitle("Change to " + currency + "?")
                        .setMessage("Every transaction in " + account.getName() + " is relabelled from "
                                + account.getCurrency() + " to " + currency + ". Amounts stay the same: "
                                + "nothing is converted.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Change", (dialog, which) -> save.run())
                        .show();
            } else {
                save.run();
            }
        });
        sheet.show();
    }

    // A fund is a purpose for money, so it gets a target; a real account is where money is, so it gets bank details
    private static void showFieldsFor(DialogAccountFormBinding form, boolean real) {
        form.tilCurrency.setVisibility(real ? View.VISIBLE : View.GONE);
        form.rowTarget.setVisibility(real ? View.GONE : View.VISIBLE);
        form.tilBank.setVisibility(real ? View.VISIBLE : View.GONE);
        form.rowNumber.setVisibility(real ? View.VISIBLE : View.GONE);
    }

    /** How far a fund with a target has got, such as "₹4,000 to go · by Mar 2027", or null without one. */
    @Nullable
    public static String targetLine(Account account, double saved, String currency) {
        if (account.isReal() || account.getTarget() == null) {
            return null;
        }
        double toGo = account.getTarget() - saved;
        StringBuilder line = new StringBuilder(toGo <= 0
                ? "Target of " + Money.format(account.getTarget(), currency) + " reached"
                : Money.format(toGo, currency) + " to go of " + Money.format(account.getTarget(), currency));
        if (account.getTargetDate() != null && toGo > 0) {
            line.append(" · by ").append(new SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(account.getTargetDate()));
        }
        return line.toString();
    }

    /** From 0 to 100, how much of its target a fund holds. */
    public static int targetPercent(Account account, double saved) {
        if (account.getTarget() == null || account.getTarget() <= 0) {
            return 0;
        }
        return (int) Math.max(0, Math.min(100, Math.round(saved * 100 / account.getTarget())));
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
        return account.isReal() ? "real account" : "fund";
    }

    private static boolean notBlank(String text) {
        return text != null && !text.trim().isEmpty();
    }

    public static void confirmDelete(FragmentActivity activity, Account account, Runnable onDeleted) {
        if (account.isDefault()) {
            Ui.snackbar(activity, "Choose another default " + typeLabel(account) + " before deleting this one");
            return;
        }
        AccountViewModel viewModel = new ViewModelProvider(activity).get(AccountViewModel.class);
        Ui.observeOnce(viewModel.getAllAccounts(), activity,
                list -> confirmDelete(activity, account, deleteMessage(account, list), onDeleted));
    }

    private static String deleteMessage(Account account, List<Account> accounts) {
        // Moving to a real account in another currency relabels the amounts without converting them
        for (Account other : accounts) {
            if (account.isReal() && other.isReal() && other.isDefault() && account.getCurrency() != null
                    && !account.getCurrency().equals(other.getCurrency())) {
                return "Its transactions move to " + other.getName() + " and are relabelled from "
                        + account.getCurrency() + " to " + other.getCurrency()
                        + ". Amounts stay the same: nothing is converted.";
            }
        }
        return "Its transactions move to your default " + typeLabel(account) + ".";
    }

    private static void confirmDelete(FragmentActivity activity, Account account, String message,
            Runnable onDeleted) {
        new MaterialAlertDialogBuilder(activity)
                .setTitle("Delete " + account.getName() + "?")
                .setMessage(message)
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
