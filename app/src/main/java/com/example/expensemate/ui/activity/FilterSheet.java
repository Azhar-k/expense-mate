package com.example.expensemate.ui.activity;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.example.expensemate.R;
import com.example.expensemate.data.RecurringPayment;
import com.example.expensemate.databinding.SheetFiltersBinding;
import com.example.expensemate.ui.common.ExpandedSheet;
import com.example.expensemate.ui.common.Ui;
import com.example.expensemate.util.Currencies;
import com.example.expensemate.util.Money;
import com.example.expensemate.viewmodel.AccountViewModel;
import com.example.expensemate.viewmodel.RecurringPaymentsViewModel;
import com.example.expensemate.viewmodel.TransactionFilter;
import com.example.expensemate.viewmodel.TransactionViewModel;

import java.util.ArrayList;
import java.util.List;

/** The filters used less often than search, date, account and category. */
public class FilterSheet extends ExpandedSheet {
    private static final String ANY_BILL = "Any";
    private static final String ANY_CURRENCY = "Any";

    private SheetFiltersBinding binding;
    private TransactionViewModel viewModel;
    private List<RecurringPayment> bills = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = SheetFiltersBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        ViewModelProvider provider = new ViewModelProvider(requireActivity());
        viewModel = provider.get(TransactionViewModel.class);
        TransactionFilter current = viewModel.getFilter().getValue();

        if (Boolean.TRUE.equals(current.transfers)) {
            binding.chipsType.check(R.id.typeTransfer);
        } else if ("DEBIT".equals(current.transactionType)) {
            binding.chipsType.check(R.id.typeExpense);
        } else if ("CREDIT".equals(current.transactionType)) {
            binding.chipsType.check(R.id.typeIncome);
        } else {
            binding.chipsType.check(R.id.typeAll);
        }
        binding.etReceiver.setText(current.receiver);
        binding.etDescription.setText(current.description);
        if (current.amount != null) {
            binding.etAmount.setText(Money.plain(current.amount));
        }
        binding.switchExcluded.setChecked(Boolean.TRUE.equals(current.excluded));
        binding.etBill.setText(ANY_BILL, false);

        binding.etCurrency.setText(current.currency != null ? current.currency : ANY_CURRENCY, false);
        provider.get(AccountViewModel.class).getAllAccounts().observe(getViewLifecycleOwner(), accounts -> {
            List<String> codes = new ArrayList<>(Currencies.inUse(accounts));
            if (current.currency != null && !codes.contains(current.currency)) {
                codes.add(current.currency);
            }
            codes.sort(Currencies.mainFirst(Currencies.main(accounts)));
            binding.tilCurrency.setVisibility(codes.size() > 1 ? View.VISIBLE : View.GONE);
            codes.add(0, ANY_CURRENCY);
            Ui.setDropdownItems(binding.etCurrency, codes);
        });

        provider.get(RecurringPaymentsViewModel.class).getRecurringPayments().observe(getViewLifecycleOwner(), list -> {
            bills = list;
            List<String> names = new ArrayList<>();
            names.add(ANY_BILL);
            for (RecurringPayment bill : list) {
                names.add(bill.getName());
                if (current.billId != null && current.billId == bill.getId()) {
                    binding.etBill.setText(bill.getName(), false);
                }
            }
            Ui.setDropdownItems(binding.etBill, names);
        });

        binding.btnClear.setOnClickListener(v -> {
            TransactionFilter cleared = current.copy();
            clearMore(cleared);
            viewModel.setFilter(cleared);
            dismiss();
        });
        binding.btnApply.setOnClickListener(v -> apply(current));
    }

    private void apply(TransactionFilter current) {
        String amountText = binding.etAmount.getText().toString().trim();
        Double amount = Money.parse(amountText);
        if (!amountText.isEmpty() && amount == null) {
            binding.tilAmount.setError("Enter an amount above zero");
            return;
        }
        TransactionFilter updated = current.copy();
        clearMore(updated);
        updated.amount = amount;
        updated.currency = Currencies.normalize(binding.etCurrency.getText().toString());
        updated.receiver = blankToNull(binding.etReceiver.getText().toString());
        updated.description = blankToNull(binding.etDescription.getText().toString());
        updated.excluded = binding.switchExcluded.isChecked() ? Boolean.TRUE : null;

        int type = binding.chipsType.getCheckedChipId();
        if (type == R.id.typeTransfer) {
            updated.transfers = true;
        } else if (type == R.id.typeExpense) {
            updated.transactionType = "DEBIT";
            updated.transfers = false;
        } else if (type == R.id.typeIncome) {
            updated.transactionType = "CREDIT";
            updated.transfers = false;
        }

        String billName = binding.etBill.getText().toString();
        for (RecurringPayment bill : bills) {
            if (bill.getName().equals(billName)) {
                updated.billId = bill.getId();
            }
        }
        viewModel.setFilter(updated);
        dismiss();
    }

    private static void clearMore(TransactionFilter filter) {
        filter.amount = null;
        filter.currency = null;
        filter.receiver = null;
        filter.description = null;
        filter.excluded = null;
        filter.transfers = null;
        filter.transactionType = null;
        filter.billId = null;
        filter.needsReview = null;
    }

    private static String blankToNull(String text) {
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
