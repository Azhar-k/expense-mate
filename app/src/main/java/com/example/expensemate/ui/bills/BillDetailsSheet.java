package com.example.expensemate.ui.bills;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.expensemate.data.RecurringPayment;
import com.example.expensemate.databinding.SheetBillDetailsBinding;
import com.example.expensemate.ui.common.ExpandedSheet;
import com.example.expensemate.ui.common.TransactionText;
import com.example.expensemate.ui.common.Ui;
import com.example.expensemate.util.Bills;
import com.example.expensemate.util.Money;
import com.example.expensemate.viewmodel.AccountViewModel;
import com.example.expensemate.viewmodel.RecurringPaymentsViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.Map;

public class BillDetailsSheet extends ExpandedSheet {
    private static final String ARG_ID = "id";

    private SheetBillDetailsBinding binding;

    public static void show(FragmentActivity activity, long billId) {
        Bundle args = new Bundle();
        args.putLong(ARG_ID, billId);
        BillDetailsSheet sheet = new BillDetailsSheet();
        sheet.setArguments(args);
        sheet.show(activity.getSupportFragmentManager(), "bill_details");
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = SheetBillDetailsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        ViewModelProvider provider = new ViewModelProvider(requireActivity());
        RecurringPaymentsViewModel billsViewModel = provider.get(RecurringPaymentsViewModel.class);
        long id = requireArguments().getLong(ARG_ID);

        provider.get(AccountViewModel.class).getAllAccounts().observe(getViewLifecycleOwner(),
                accounts -> Ui.observeOnce(billsViewModel.getRecurringPayments(), getViewLifecycleOwner(), bills -> {
                    for (RecurringPayment bill : bills) {
                        if (bill.getId() == id) {
                            render(bill, BillActions.names(accounts), billsViewModel);
                            return;
                        }
                    }
                    dismissAllowingStateLoss();
                }));
    }

    private void render(RecurringPayment bill, Map<Long, String> names, RecurringPaymentsViewModel billsViewModel) {
        Calendar today = Calendar.getInstance();
        SimpleDateFormat dateFormat = new SimpleDateFormat("d MMM yyyy", Locale.getDefault());
        Bills.Status status = Bills.statusOf(bill, today);

        binding.tvName.setText(bill.getName());
        binding.tvAmount.setText(Money.format(bill.getAmount(), bill.getCurrency()));
        binding.details.removeAllViews();
        Ui.addKeyValue(binding.details, "This month", BillText.statusLine(bill, today));
        Ui.addKeyValue(binding.details, "Repeats", "Day " + bill.getDueDay() + " of every month");
        if (bill.getExpiryDate() != null) {
            Ui.addKeyValue(binding.details, status == Bills.Status.ENDED ? "Ended on" : "Ends on",
                    dateFormat.format(bill.getExpiryDate()));
        }
        if (Bills.isTransfer(bill)) {
            Ui.addKeyValue(binding.details, "From", TransactionText.accountName(names, bill.getFromAccountId()));
            Ui.addKeyValue(binding.details, "To", TransactionText.accountName(names, bill.getToAccountId()));
        }
        if (bill.getLastCompletedDate() != null) {
            Ui.addKeyValue(binding.details, "Last done", dateFormat.format(bill.getLastCompletedDate()));
        }

        FragmentActivity activity = requireActivity();
        boolean canMove = Bills.isTransfer(bill) && status != Bills.Status.PAID && status != Bills.Status.ENDED
                && !bill.getFromAccountId().equals(bill.getToAccountId());
        binding.btnMove.setVisibility(canMove ? View.VISIBLE : View.GONE);
        binding.btnMove.setText("Move " + Money.format(bill.getAmount(), bill.getCurrency()));
        binding.btnMove.setOnClickListener(v -> {
            dismiss();
            BillActions.confirmMove(activity, bill, names);
        });
        binding.btnEdit.setText(status == Bills.Status.ENDED ? "Renew" : "Edit");
        binding.btnEdit.setOnClickListener(v -> {
            dismiss();
            BillForm.show(activity, bill);
        });
        binding.btnDelete.setOnClickListener(v -> new MaterialAlertDialogBuilder(activity)
                .setTitle("Delete " + bill.getName() + "?")
                .setMessage("Transactions linked to it stay in your history.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    billsViewModel.delete(bill);
                    dismiss();
                    Ui.snackbar(activity, bill.getName() + " deleted");
                })
                .show());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
