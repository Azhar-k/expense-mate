package com.example.expensemate.ui.loans;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.expensemate.data.LoanSummary;
import com.example.expensemate.data.Transaction;
import com.example.expensemate.databinding.SheetLoanDetailsBinding;
import com.example.expensemate.ui.activity.TransactionDetailsSheet;
import com.example.expensemate.ui.common.ExpandedSheet;
import com.example.expensemate.ui.common.TransactionText;
import com.example.expensemate.ui.common.Ui;
import com.example.expensemate.util.Money;
import com.example.expensemate.viewmodel.LoanViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

/** One loan: how much is back, the transactions behind it, and what can be done with it. */
public class LoanDetailsSheet extends ExpandedSheet {
    private static final String ARG_ID = "id";

    private SheetLoanDetailsBinding binding;
    private LoanViewModel viewModel;
    private LoanSummary summary;
    private List<Transaction> linked = new ArrayList<>();

    public static void show(FragmentActivity activity, long loanId) {
        Bundle args = new Bundle();
        args.putLong(ARG_ID, loanId);
        LoanDetailsSheet sheet = new LoanDetailsSheet();
        sheet.setArguments(args);
        sheet.show(activity.getSupportFragmentManager(), "loan_details");
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = SheetLoanDetailsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(requireActivity()).get(LoanViewModel.class);
        long id = requireArguments().getLong(ARG_ID);

        viewModel.getLoans().observe(getViewLifecycleOwner(), loans -> {
            for (LoanSummary item : loans) {
                if (item.loan.getId() == id) {
                    summary = item;
                    render();
                    return;
                }
            }
            dismissAllowingStateLoss();
        });
        viewModel.getLinkedTransactions(id).observe(getViewLifecycleOwner(), list -> {
            linked = list;
            render();
        });

        binding.btnRecordReturn.setOnClickListener(v -> LoanActions.recordReturn(requireActivity(), summary));
        binding.btnEdit.setOnClickListener(v -> {
            FragmentActivity activity = requireActivity();
            dismiss();
            LoanForm.showEdit(activity, summary.loan);
        });
        binding.btnDelete.setOnClickListener(v -> new MaterialAlertDialogBuilder(requireActivity())
                .setTitle("Delete money lent to " + summary.loan.getPerson() + "?")
                .setMessage("Linked transactions stay in your history.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    FragmentActivity activity = requireActivity();
                    viewModel.delete(summary.loan);
                    dismiss();
                    Ui.snackbar(activity, "Deleted");
                })
                .show());
    }

    private void render() {
        if (binding == null || summary == null) {
            return;
        }
        boolean completed = summary.isCompleted();
        binding.tvPerson.setText(summary.loan.getPerson());
        binding.tvStatus.setText(!completed ? "To get back"
                : summary.isFullyReturned() ? "Returned" : "Lent, marked as returned");
        binding.tvAmount.setText(Money.format(completed ? summary.loan.getAmount() : summary.outstanding(), summary.loan.getCurrency()));

        binding.details.removeAllViews();
        Ui.addKeyValue(binding.details, "Lent", Money.format(summary.loan.getAmount(), summary.loan.getCurrency()) + " on "
                + LoanText.date(summary.loan.getDate()));
        Ui.addKeyValue(binding.details, "Returned", Money.format(summary.returned, summary.loan.getCurrency()));
        if (summary.loan.getReturnedManually() > 0) {
            Ui.addKeyValue(binding.details, "Recorded by hand", Money.format(summary.loan.getReturnedManually(), summary.loan.getCurrency()));
        }
        if (completed && summary.completedDate() != null) {
            Ui.addKeyValue(binding.details, "Completed on", LoanText.date(summary.completedDate()));
        }
        if (TransactionText.hasText(summary.loan.getNote())) {
            Ui.addKeyValue(binding.details, "Note", summary.loan.getNote());
        }

        binding.headerLinked.setVisibility(linked.isEmpty() ? View.GONE : View.VISIBLE);
        binding.listLinked.removeAllViews();
        for (Transaction transaction : linked) {
            boolean credit = "CREDIT".equals(transaction.getTransactionType());
            Ui.addKeyValue(binding.listLinked, (credit ? "Returned " : "Lent ") + LoanText.date(transaction.getDate()),
                    credit ? Money.credit(transaction.getAmount(), transaction.getCurrency()) : Money.debit(transaction.getAmount(), transaction.getCurrency()));
            View row = binding.listLinked.getChildAt(binding.listLinked.getChildCount() - 1);
            row.setBackgroundResource(android.R.drawable.list_selector_background);
            row.setOnClickListener(v -> {
                FragmentActivity activity = requireActivity();
                dismiss();
                TransactionDetailsSheet.show(activity, transaction.getId());
            });
        }

        // A loan that is fully back cannot be reopened; unlinking a return is how to undo that
        binding.btnRecordReturn.setVisibility(completed ? View.GONE : View.VISIBLE);
        binding.btnClose.setVisibility(completed && summary.isFullyReturned() ? View.GONE : View.VISIBLE);
        binding.btnClose.setText(completed ? "Reopen" : "Mark as returned");
        binding.btnClose.setOnClickListener(v -> {
            if (summary.isCompleted()) {
                viewModel.reopen(summary.loan);
            } else {
                viewModel.markReturned(summary.loan);
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
