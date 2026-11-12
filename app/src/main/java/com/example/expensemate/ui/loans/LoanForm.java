package com.example.expensemate.ui.loans;

import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.expensemate.data.Loan;
import com.example.expensemate.data.Transaction;
import com.example.expensemate.databinding.DialogLoanFormBinding;
import com.example.expensemate.ui.common.CurrencyField;
import com.example.expensemate.ui.common.FormSheet;
import com.example.expensemate.ui.common.Pickers;
import com.example.expensemate.ui.common.TransactionText;
import com.example.expensemate.ui.common.Ui;
import com.example.expensemate.util.Currencies;
import com.example.expensemate.util.Money;
import com.example.expensemate.viewmodel.AccountViewModel;
import com.example.expensemate.viewmodel.LoanViewModel;

import java.util.Calendar;

/** Adds money lent, by hand or from the payment that sent it, or edits one. */
public final class LoanForm {
    private LoanForm() {
    }

    public static void showAdd(FragmentActivity activity) {
        show(activity, null, null);
    }

    public static void showEdit(FragmentActivity activity, Loan loan) {
        show(activity, loan, null);
    }

    /** Starts from a debit, which stays linked to the new loan. */
    public static void showFromTransaction(FragmentActivity activity, Transaction from) {
        show(activity, null, from);
    }

    private static void show(FragmentActivity activity, @Nullable Loan editing, @Nullable Transaction from) {
        DialogLoanFormBinding form = DialogLoanFormBinding.inflate(LayoutInflater.from(activity));
        Calendar when = Calendar.getInstance();

        if (editing != null) {
            form.etPerson.setText(editing.getPerson());
            form.etAmount.setText(Money.plain(editing.getAmount()));
            form.etNote.setText(editing.getNote());
            if (editing.getDate() != null) {
                when.setTime(editing.getDate());
            }
        } else if (from != null) {
            if (TransactionText.hasText(from.getReceiverName())) {
                form.etPerson.setText(from.getReceiverName());
            }
            form.etAmount.setText(Money.plain(from.getAmount()));
            when.setTime(from.getDate());
            form.tvLinked.setVisibility(View.VISIBLE);
            form.tvLinked.setText("Linked to the payment of " + Money.format(from.getAmount(), from.getCurrency()) + " on "
                    + LoanText.date(from.getDate()) + ".");
        }
        // Lent through a payment, it is in that payment's currency
        if (from != null) {
            form.tilCurrency.setVisibility(View.GONE);
        } else {
            Ui.observeOnce(new ViewModelProvider(activity).get(AccountViewModel.class).getAllAccounts(), activity,
                    list -> {
                        String main = Currencies.main(list);
                        CurrencyField.setUp(form.etCurrency, editing != null ? editing.getCurrency() : main, main,
                                Currencies.inUse(list));
                    });
        }
        form.etDate.setText(LoanText.date(when.getTime()));
        form.etDate.setOnClickListener(v -> Pickers.pickDate(activity.getSupportFragmentManager(), "Lent on", when,
                () -> form.etDate.setText(LoanText.date(when.getTime()))));

        FormSheet sheet = FormSheet.create(activity, editing == null ? "Add money lent" : "Edit money lent",
                form.getRoot());
        sheet.onSave(() -> {
            String person = form.etPerson.getText().toString().trim();
            Double amount = Money.parse(form.etAmount.getText().toString());
            String currency = from != null ? from.getCurrency() : CurrencyField.read(form.etCurrency);
            form.tilCurrency.setError(currency == null ? "Pick a currency" : null);
            form.tilPerson.setError(person.isEmpty() ? "Who did you lend it to?" : null);
            form.tilAmount.setError(amount == null ? "Enter an amount" : null);
            if (person.isEmpty() || amount == null || currency == null) {
                return;
            }

            Loan loan = editing != null ? editing : new Loan();
            loan.setPerson(person);
            loan.setAmount(amount);
            loan.setCurrency(currency);
            loan.setDate(when.getTime());
            String note = form.etNote.getText().toString().trim();
            loan.setNote(note.isEmpty() ? null : note);

            LoanViewModel viewModel = new ViewModelProvider(activity).get(LoanViewModel.class);
            if (editing != null) {
                viewModel.update(loan);
            } else if (from != null) {
                viewModel.addFromTransaction(loan, from.getId());
            } else {
                viewModel.add(loan);
            }
            sheet.dismiss();
        });
        sheet.show();
    }
}
