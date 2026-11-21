package com.example.expensemate.ui.bills;

import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.expensemate.R;
import com.example.expensemate.data.BillPayment;
import com.example.expensemate.data.RecurringPayment;
import com.example.expensemate.databinding.SheetBillDetailsBinding;
import com.example.expensemate.ui.common.ExpandedSheet;
import com.example.expensemate.ui.common.TransactionText;
import com.example.expensemate.ui.common.Ui;
import com.example.expensemate.util.BillLedger;
import com.example.expensemate.util.Bills;
import com.example.expensemate.util.Money;
import com.example.expensemate.util.Periods;
import com.example.expensemate.viewmodel.FundViewModel;
import com.example.expensemate.viewmodel.RecurringPaymentsViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** One bill: how it stands in the month it was opened from, and its last twelve months. */
public class BillDetailsSheet extends ExpandedSheet {
    private static final String ARG_ID = "id";
    private static final String ARG_PERIOD = "period";
    private static final int HISTORY_MONTHS = 12;

    private SheetBillDetailsBinding binding;
    private List<RecurringPayment> bills = new ArrayList<>();
    private BillLedger ledger;
    private Map<Long, String> names;

    public static void show(FragmentActivity activity, long billId, int period) {
        Bundle args = new Bundle();
        args.putLong(ARG_ID, billId);
        args.putInt(ARG_PERIOD, period);
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

        // Stays open while its month is paid, skipped or undone, so it follows along
        provider.get(FundViewModel.class).getAllFunds().observe(getViewLifecycleOwner(), funds -> {
            names = BillActions.names(funds);
            update(billsViewModel);
        });
        billsViewModel.getRecurringPayments().observe(getViewLifecycleOwner(), list -> {
            bills = list;
            update(billsViewModel);
        });
        billsViewModel.getPayments().observe(getViewLifecycleOwner(), payments -> {
            ledger = BillLedger.of(payments);
            update(billsViewModel);
        });
    }

    private void update(RecurringPaymentsViewModel billsViewModel) {
        if (binding == null || names == null || ledger == null || bills.isEmpty()) {
            return;
        }
        long id = requireArguments().getLong(ARG_ID);
        for (RecurringPayment bill : bills) {
            if (bill.getId() == id) {
                render(bill, billsViewModel);
                return;
            }
        }
        dismissAllowingStateLoss();
    }

    private void render(RecurringPayment bill, RecurringPaymentsViewModel billsViewModel) {
        Calendar today = Calendar.getInstance();
        int period = requireArguments().getInt(ARG_PERIOD, Periods.current());
        SimpleDateFormat dateFormat = new SimpleDateFormat("d MMM yyyy", Locale.getDefault());
        Bills.Status status = Bills.statusOf(bill, ledger, period, today);
        String month = Periods.name(period);

        binding.tvName.setText(bill.getName());
        binding.tvAmount.setText(Money.format(bill.getAmount(), bill.getCurrency()));
        binding.details.removeAllViews();
        Ui.addKeyValue(binding.details, period == Periods.current() ? "This month" : month,
                BillText.statusLine(bill, ledger, period, today));
        BillPayment row = ledger.get(bill.getId(), period);
        if (row != null && !row.isSkipped()
                && (row.getAmount() != bill.getAmount() || !row.getCurrency().equals(bill.getCurrency()))) {
            Ui.addKeyValue(binding.details, "Paid", Money.format(row.getAmount(), row.getCurrency()));
        }
        Ui.addKeyValue(binding.details, "Repeats", "Day " + bill.getDueDay() + " of every month");
        if (bill.getExpiryDate() != null) {
            Ui.addKeyValue(binding.details, bill.getExpiryDate().before(today.getTime()) ? "Ended on" : "Ends on",
                    dateFormat.format(bill.getExpiryDate()));
        }
        if (Bills.isTransfer(bill)) {
            Ui.addKeyValue(binding.details, "From", TransactionText.fundName(names, bill.getFromFundId()));
            Ui.addKeyValue(binding.details, "To", TransactionText.fundName(names, bill.getToFundId()));
        }
        renderHistory(bill, today);

        FragmentActivity activity = requireActivity();
        boolean canMove = Bills.isTransfer(bill) && Bills.isOwed(status)
                && !bill.getFromFundId().equals(bill.getToFundId());
        binding.btnMove.setVisibility(canMove ? View.VISIBLE : View.GONE);
        binding.btnMove.setText("Top up " + Money.format(bill.getAmount(), bill.getCurrency()));
        binding.btnMove.setOnClickListener(v -> {
            dismiss();
            BillActions.confirmMove(activity, bill, names, period);
        });

        if (Bills.isOwed(status)) {
            binding.btnSkip.setVisibility(View.VISIBLE);
            binding.btnSkip.setText("Skip " + month);
            binding.btnSkip.setOnClickListener(v -> billsViewModel.skip(bill, period));
        } else if (Bills.isDone(status)) {
            binding.btnSkip.setVisibility(View.VISIBLE);
            binding.btnSkip.setText(status == Bills.Status.SKIPPED ? "Undo skip" : "Mark " + month + " unpaid");
            binding.btnSkip.setOnClickListener(v -> billsViewModel.markUnpaid(bill, period));
        } else {
            binding.btnSkip.setVisibility(View.GONE);
        }

        boolean ended = bill.getExpiryDate() != null && bill.getExpiryDate().before(today.getTime());
        binding.btnEdit.setText(ended ? "Renew" : "Edit");
        binding.btnEdit.setOnClickListener(v -> {
            dismiss();
            BillForm.show(activity, bill);
        });
        binding.btnDelete.setOnClickListener(v -> new MaterialAlertDialogBuilder(activity)
                .setTitle("Delete " + bill.getName() + "?")
                .setMessage("Its paid months go too. Transactions linked to it stay in your history.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    billsViewModel.delete(bill);
                    dismiss();
                    Ui.snackbar(activity, bill.getName() + " deleted");
                })
                .show());
    }

    /** A column per month, oldest on the left: ✓ paid, – skipped, ! missed, · still owed. */
    private void renderHistory(RecurringPayment bill, Calendar today) {
        binding.history.removeAllViews();
        int current = Periods.current();
        int paid = 0;
        int missed = 0;
        for (int back = HISTORY_MONTHS - 1; back >= 0; back--) {
            int month = Periods.plus(current, -back);
            Bills.Status status = Bills.statusOf(bill, ledger, month, today);
            String mark;
            int color;
            switch (status) {
                case PAID:
                    mark = "✓";
                    color = R.color.primary;
                    paid++;
                    break;
                case SKIPPED:
                    mark = "–";
                    color = R.color.on_surface_variant;
                    break;
                case LATE:
                    mark = "!";
                    color = R.color.alert;
                    if (month < current) {
                        missed++;
                    }
                    break;
                case DUE_SOON:
                case UPCOMING:
                    mark = "·";
                    color = R.color.on_surface_variant;
                    break;
                default:
                    mark = " ";
                    color = R.color.on_surface_variant;
                    break;
            }
            binding.history.addView(historyCell(Periods.shortName(month).substring(0, 1), mark, color,
                    Periods.name(month) + ": " + describe(status, month < current)));
        }
        StringBuilder summary = new StringBuilder("Paid in ").append(paid).append(paid == 1 ? " month" : " months");
        if (missed > 0) {
            summary.append(" · missed ").append(missed);
        }
        binding.tvHistorySummary.setText(summary);
    }

    private static String describe(Bills.Status status, boolean over) {
        switch (status) {
            case PAID:
                return "paid";
            case SKIPPED:
                return "skipped";
            case LATE:
                return over ? "missed" : "late";
            case DUE_SOON:
            case UPCOMING:
                return "to pay";
            default:
                return "not due";
        }
    }

    private View historyCell(String initial, String mark, int color, String description) {
        LinearLayout cell = new LinearLayout(requireContext());
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        cell.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        cell.setContentDescription(description);

        TextView markView = new TextView(requireContext(), null, 0, R.style.Text_ExpenseMate_Row);
        markView.setText(mark);
        markView.setTextColor(ContextCompat.getColor(requireContext(), color));
        markView.setGravity(Gravity.CENTER);
        markView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        cell.addView(markView);

        TextView monthView = new TextView(requireContext(), null, 0, R.style.Text_ExpenseMate_Sub);
        monthView.setText(initial);
        monthView.setGravity(Gravity.CENTER);
        monthView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        cell.addView(monthView);
        return cell;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
