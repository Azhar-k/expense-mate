package com.example.expensemate.ui.bills;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;

import com.example.expensemate.R;
import com.example.expensemate.data.RecurringPayment;
import com.example.expensemate.databinding.FragmentBillsBinding;
import com.example.expensemate.databinding.ItemBillRowBinding;
import com.example.expensemate.ui.common.Ui;
import com.example.expensemate.util.Bills;
import com.example.expensemate.util.Currencies;
import com.example.expensemate.util.CurrencyTotals;
import com.example.expensemate.util.Money;
import com.example.expensemate.viewmodel.AccountViewModel;
import com.example.expensemate.viewmodel.RecurringPaymentsViewModel;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class BillsFragment extends Fragment {
    private FragmentBillsBinding binding;
    private RecurringPaymentsViewModel viewModel;
    private List<RecurringPayment> allBills = new ArrayList<>();
    private Map<Long, String> accountNames = new HashMap<>();
    private String mainCurrency = Currencies.fromLocale();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentBillsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        ViewModelProvider provider = new ViewModelProvider(requireActivity());
        viewModel = provider.get(RecurringPaymentsViewModel.class);

        requireActivity().addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
                menu.add(Menu.NONE, 1, Menu.NONE, "Mark all as paid");
                menu.add(Menu.NONE, 2, Menu.NONE, "Mark all as unpaid");
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem item) {
                if (item.getItemId() == 1 || item.getItemId() == 2) {
                    markAll(item.getItemId() == 1);
                    return true;
                }
                return false;
            }
        }, getViewLifecycleOwner(), Lifecycle.State.RESUMED);

        binding.fabAdd.setOnClickListener(v -> BillForm.show(requireActivity(), null));
        provider.get(AccountViewModel.class).getAllAccounts().observe(getViewLifecycleOwner(), accounts -> {
            accountNames = BillActions.names(accounts);
            mainCurrency = Currencies.main(accounts);
            render();
        });
        viewModel.getRecurringPayments().observe(getViewLifecycleOwner(), bills -> {
            allBills = bills;
            render();
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        ((AppCompatActivity) requireActivity()).getSupportActionBar().setTitle(
                new SimpleDateFormat("MMMM", Locale.getDefault()).format(Calendar.getInstance().getTime()) + " bills");
    }

    private void markAll(boolean paid) {
        Calendar today = Calendar.getInstance();
        for (RecurringPayment bill : allBills) {
            if (Bills.statusOf(bill, today) == Bills.Status.ENDED || Bills.isPaid(bill, today) == paid) {
                continue;
            }
            if (paid) {
                viewModel.markAsCompleted(bill);
            } else {
                viewModel.resetCompletionStatus(bill);
            }
        }
    }

    private void render() {
        if (binding == null) {
            return;
        }
        Calendar today = Calendar.getInstance();
        List<RecurringPayment> bills = new ArrayList<>();
        List<RecurringPayment> transfers = new ArrayList<>();
        List<RecurringPayment> ended = new ArrayList<>();
        CurrencyTotals total = new CurrencyTotals();
        CurrencyTotals billsLeft = new CurrencyTotals();
        CurrencyTotals transfersLeft = new CurrencyTotals();
        int done = 0;
        for (RecurringPayment bill : Bills.sortByUrgency(allBills, today)) {
            Bills.Status status = Bills.statusOf(bill, today);
            if (status == Bills.Status.ENDED) {
                ended.add(bill);
                continue;
            }
            boolean transfer = Bills.isTransfer(bill);
            (transfer ? transfers : bills).add(bill);
            total.add(bill.getCurrency(), bill.getAmount());
            if (status == Bills.Status.PAID) {
                done++;
            } else if (transfer) {
                transfersLeft.add(bill.getCurrency(), bill.getAmount());
            } else {
                billsLeft.add(bill.getCurrency(), bill.getAmount());
            }
        }

        CurrencyTotals left = new CurrencyTotals().addAll(billsLeft).addAll(transfersLeft);
        CurrencyTotals paid = new CurrencyTotals().addAll(total);
        for (java.util.Map.Entry<String, Double> each : left.nonZero(mainCurrency).entrySet()) {
            paid.add(each.getKey(), -each.getValue());
        }
        int count = bills.size() + transfers.size();
        binding.tvRemaining.setText(left.format(mainCurrency, mainCurrency));
        Ui.styleTag(binding.tvDoneCount, done + " of " + count + " done", R.color.primary_container,
                R.color.on_primary_container);
        // Amounts in different currencies cannot be added, so with more than one the bar counts bills instead
        java.util.Map<String, Double> totals = total.nonZero(mainCurrency);
        if (totals.size() == 1) {
            String only = totals.keySet().iterator().next();
            binding.progressPaid.setProgress((int) Math.round(paid.get(only) * 100 / total.get(only)));
        } else {
            binding.progressPaid.setProgress(count > 0 ? done * 100 / count : 0);
        }
        binding.tvPaidSummary.setText(paidSummary(paid, total));
        binding.tvBillsLeft.setText(billsLeft.format(mainCurrency, mainCurrency) + " left");
        binding.tvTransfersLeft.setText(transfersLeft.format(mainCurrency, mainCurrency) + " left");

        fillList(binding.listBills, bills, today, "No bills yet. Add rent, subscriptions or anything else that repeats.");
        fillList(binding.listTransfers, transfers, today, "Add a bill with both accounts to plan a regular transfer.");
        fillList(binding.listEnded, ended, today, null);
        binding.headerEnded.setVisibility(ended.isEmpty() ? View.GONE : View.VISIBLE);

        List<RecurringPayment> pending = Bills.pendingTransfers(allBills, today);
        CurrencyTotals pendingTotal = new CurrencyTotals();
        for (RecurringPayment bill : pending) {
            pendingTotal.add(bill.getCurrency(), bill.getAmount());
        }
        binding.btnMoveAll.setVisibility(pending.isEmpty() ? View.GONE : View.VISIBLE);
        binding.btnMoveAll.setText("Move all " + pendingTotal.format(mainCurrency, mainCurrency));
        binding.btnMoveAll.setOnClickListener(
                v -> BillActions.confirmMoveAll(requireActivity(), pending, accountNames));
    }

    /** "₹500 paid of ₹900", or with several currencies "₹500 of ₹900 · AED 0 of AED 300 paid". */
    private String paidSummary(CurrencyTotals paid, CurrencyTotals total) {
        java.util.Map<String, Double> totals = total.nonZero(mainCurrency);
        if (totals.size() < 2) {
            return paid.format(mainCurrency, mainCurrency) + " paid of " + total.format(mainCurrency, mainCurrency);
        }
        StringBuilder text = new StringBuilder();
        for (java.util.Map.Entry<String, Double> each : totals.entrySet()) {
            if (text.length() > 0) {
                text.append(" · ");
            }
            text.append(Money.format(paid.get(each.getKey()), each.getKey())).append(" of ")
                    .append(Money.format(each.getValue(), each.getKey()));
        }
        return text.append(" paid").toString();
    }

    private void fillList(LinearLayout list, List<RecurringPayment> bills, Calendar today, @Nullable String emptyText) {
        list.removeAllViews();
        if (bills.isEmpty() && emptyText != null) {
            View empty = getLayoutInflater().inflate(R.layout.item_list_empty, list, false);
            ((android.widget.TextView) empty).setText(emptyText);
            list.addView(empty);
        }
        for (int i = 0; i < bills.size(); i++) {
            boolean first = i == 0;
            boolean last = i == bills.size() - 1;
            list.addView(buildRow(list, bills.get(i), today, first, last));
        }
    }

    private View buildRow(LinearLayout parent, RecurringPayment bill, Calendar today, boolean first, boolean last) {
        ItemBillRowBinding row = ItemBillRowBinding.inflate(getLayoutInflater(), parent, false);
        Bills.Status status = Bills.statusOf(bill, today);
        boolean paid = status == Bills.Status.PAID;
        boolean ended = status == Bills.Status.ENDED;

        row.getRoot().setBackgroundResource(first && last ? R.drawable.bg_row_single
                : first ? R.drawable.bg_row_top : last ? R.drawable.bg_row_bottom : R.drawable.bg_row_middle);
        row.divider.setVisibility(last ? View.GONE : View.VISIBLE);
        row.tvName.setText(bill.getName());
        row.tvAmount.setText(Money.format(bill.getAmount(), bill.getCurrency()));

        String statusLine = BillText.statusLine(bill, today);
        if (Bills.isTransfer(bill)) {
            String route = accountNames.get(bill.getFromAccountId()) + " → " + accountNames.get(bill.getToAccountId());
            statusLine = route + " · " + statusLine;
        }
        row.tvStatus.setText(statusLine);
        row.tvStatus.setTextColor(ContextCompat.getColor(requireContext(),
                status == Bills.Status.LATE ? R.color.alert : R.color.on_surface_variant));

        row.ivCircle.setBackgroundResource(paid ? R.drawable.bg_circle_done : R.drawable.bg_circle_empty);
        if (paid) {
            Ui.tintImage(row.ivCircle, R.drawable.ic_check, R.color.on_primary);
        }
        if (ended) {
            row.togglePaid.setVisibility(View.INVISIBLE);
            row.tvName.setTextColor(ContextCompat.getColor(requireContext(), R.color.on_surface_variant));
        } else {
            row.togglePaid.setContentDescription(paid ? "Mark " + bill.getName() + " as unpaid"
                    : "Mark " + bill.getName() + " as paid");
            row.togglePaid.setOnClickListener(v -> {
                if (paid) {
                    viewModel.resetCompletionStatus(bill);
                } else {
                    viewModel.markAsCompleted(bill);
                }
            });
        }

        boolean canMove = Bills.isTransfer(bill) && !paid && !ended
                && !bill.getFromAccountId().equals(bill.getToAccountId());
        if (canMove) {
            Ui.styleTag(row.btnMove, "Move now", R.color.transfer_container, R.color.transfer);
            row.btnMove.setOnClickListener(v -> BillActions.confirmMove(requireActivity(), bill, accountNames));
        }
        row.getRoot().setOnClickListener(v -> BillDetailsSheet.show(requireActivity(), bill.getId()));
        return row.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
