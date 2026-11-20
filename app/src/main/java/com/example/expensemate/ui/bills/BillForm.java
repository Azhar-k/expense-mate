package com.example.expensemate.ui.bills;

import android.view.LayoutInflater;
import android.widget.AutoCompleteTextView;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.expensemate.data.Fund;
import com.example.expensemate.data.RecurringPayment;
import com.example.expensemate.databinding.DialogBillFormBinding;
import com.example.expensemate.ui.common.CurrencyField;
import com.example.expensemate.ui.common.FormSheet;
import com.example.expensemate.ui.common.Pickers;
import com.example.expensemate.ui.common.Ui;
import com.example.expensemate.util.Currencies;
import com.example.expensemate.util.Money;
import com.example.expensemate.viewmodel.AccountViewModel;
import com.example.expensemate.viewmodel.FundViewModel;
import com.example.expensemate.viewmodel.RecurringPaymentsViewModel;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** Adds or edits a recurring payment. Editing is also how an ended one gets renewed. */
public final class BillForm {
    private static final String NO_FUND = "None";

    private BillForm() {
    }

    public static void show(FragmentActivity activity, @Nullable RecurringPayment bill) {
        DialogBillFormBinding form = DialogBillFormBinding.inflate(LayoutInflater.from(activity));
        SimpleDateFormat dateFormat = new SimpleDateFormat("d MMM yyyy", Locale.getDefault());
        Calendar ends = Calendar.getInstance();
        ends.add(Calendar.YEAR, 1);

        if (bill != null) {
            form.etName.setText(bill.getName());
            form.etAmount.setText(Money.plain(bill.getAmount()));
            form.etDueDay.setText(String.valueOf(bill.getDueDay()));
            if (bill.getExpiryDate() != null) {
                ends.setTime(bill.getExpiryDate());
            }
        }
        form.etEnds.setText(dateFormat.format(ends.getTime()));
        form.etEnds.setOnClickListener(v -> Pickers.pickDate(activity.getSupportFragmentManager(), "Ends on", ends,
                () -> form.etEnds.setText(dateFormat.format(ends.getTime()))));

        // A fund top-up moves money from one fund to another
        List<Fund> funds = new ArrayList<>();
        Ui.observeOnce(new ViewModelProvider(activity).get(FundViewModel.class).getAllFunds(), activity, list -> {
            List<String> names = new ArrayList<>();
            names.add(NO_FUND);
            for (Fund fund : list) {
                funds.add(fund);
                names.add(fund.getName());
                if (bill != null && bill.getFromFundId() != null && bill.getFromFundId() == fund.getId()) {
                    form.etFrom.setText(fund.getName(), false);
                }
                if (bill != null && bill.getToFundId() != null && bill.getToFundId() == fund.getId()) {
                    form.etTo.setText(fund.getName(), false);
                }
            }
            Ui.setDropdownItems(form.etFrom, names);
            Ui.setDropdownItems(form.etTo, names);
        });
        Ui.observeOnce(new ViewModelProvider(activity).get(AccountViewModel.class).getAllAccounts(), activity, list -> {
            String main = Currencies.main(list);
            CurrencyField.setUp(form.etCurrency, bill != null ? bill.getCurrency() : main, main, Currencies.inUse(list));
        });

        FormSheet sheet = FormSheet.create(activity, bill == null ? "Add a bill" : "Edit " + bill.getName(),
                form.getRoot());
        // Keeps the sheet open when something needs fixing
        sheet.onSave(() -> {
            String name = form.etName.getText().toString().trim();
            Double amount = parseAllowingZero(form.etAmount.getText().toString());
            Integer dueDay = parseDay(form.etDueDay.getText().toString());
            Long fromId = idOf(form.etFrom, funds);
            Long toId = idOf(form.etTo, funds);
            String currency = CurrencyField.read(form.etCurrency);
            boolean sameFund = fromId != null && fromId.equals(toId);

            form.tilName.setError(name.isEmpty() ? "Enter a name" : null);
            form.tilAmount.setError(amount == null ? "Enter an amount" : null);
            form.tilDueDay.setError(dueDay == null ? "1 to 31" : null);
            form.tilTo.setError(sameFund ? "Pick another" : null);
            form.tilCurrency.setError(currency == null ? "Pick a currency" : null);
            if (name.isEmpty() || amount == null || dueDay == null || sameFund || currency == null) {
                return;
            }

            RecurringPaymentsViewModel viewModel = new ViewModelProvider(activity).get(RecurringPaymentsViewModel.class);
            if (bill == null) {
                RecurringPayment created = new RecurringPayment(name, amount, dueDay, ends.getTime());
                created.setFromFundId(fromId);
                created.setToFundId(toId);
                created.setCurrency(currency);
                viewModel.insert(created);
            } else {
                bill.setName(name);
                bill.setAmount(amount);
                bill.setDueDay(dueDay);
                bill.setExpiryDate(ends.getTime());
                bill.setFromFundId(fromId);
                bill.setToFundId(toId);
                bill.setCurrency(currency);
                viewModel.update(bill);
            }
            sheet.dismiss();
        });
        sheet.show();
    }

    private static Long idOf(AutoCompleteTextView view, List<Fund> funds) {
        String name = view.getText().toString();
        for (Fund fund : funds) {
            if (fund.getName().equals(name)) {
                return fund.getId();
            }
        }
        return null;
    }

    // A variable bill such as a credit card can sit at zero until the statement arrives
    private static Double parseAllowingZero(String text) {
        try {
            double value = Double.parseDouble(text.trim());
            return value >= 0 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer parseDay(String text) {
        try {
            int day = Integer.parseInt(text.trim());
            return day >= 1 && day <= 31 ? day : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
