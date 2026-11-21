package com.example.expensemate.ui.bills;

import android.Manifest;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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
import com.example.expensemate.service.BillReminderScheduler;
import com.example.expensemate.ui.common.TransactionText;
import com.example.expensemate.ui.common.Ui;
import com.example.expensemate.util.BillLedger;
import com.example.expensemate.util.Bills;
import com.example.expensemate.util.Currencies;
import com.example.expensemate.util.CurrencyTotals;
import com.example.expensemate.util.Money;
import com.example.expensemate.util.Periods;
import com.example.expensemate.viewmodel.AccountViewModel;
import com.example.expensemate.viewmodel.FundViewModel;
import com.example.expensemate.viewmodel.RecurringPaymentsViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** One month of bills or fund top-ups, which can be stepped back through to see what was paid and missed. */
public class BillsFragment extends Fragment {
    /** Bills can be paid a month ahead, but no further. */
    private static final int MONTHS_AHEAD = 1;

    private FragmentBillsBinding binding;
    private RecurringPaymentsViewModel viewModel;
    private List<RecurringPayment> allBills = new ArrayList<>();
    private BillLedger ledger = BillLedger.EMPTY;
    private Map<Long, String> fundNames = new HashMap<>();
    private String mainCurrency = Currencies.fromLocale();
    // Kept on the fragment so coming back to Bills lands on the same tab and month
    private boolean showTopUps;
    private int period = Periods.current();

    private ActivityResultLauncher<String> notificationPermission;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        notificationPermission = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            if (granted) {
                BillReminderScheduler.checkNow(requireContext().getApplicationContext());
            }
        });
    }

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

        binding.tabs.selectTab(binding.tabs.getTabAt(showTopUps ? 1 : 0));
        binding.tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                showTopUps = tab.getPosition() == 1;
                render();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });
        binding.btnPrevMonth.setOnClickListener(v -> showMonth(Periods.plus(period, -1)));
        binding.btnNextMonth.setOnClickListener(v -> showMonth(Periods.plus(period, 1)));
        binding.btnThisMonth.setOnClickListener(v -> showMonth(Periods.current()));
        binding.bannerMissed.setOnClickListener(v -> pickMissed());
        binding.fabAdd.setOnClickListener(v -> BillForm.show(requireActivity(), null, showTopUps));
        provider.get(FundViewModel.class).getAllFunds().observe(getViewLifecycleOwner(), funds -> {
            fundNames = BillActions.names(funds);
            render();
        });
        provider.get(AccountViewModel.class).getAllAccounts().observe(getViewLifecycleOwner(), accounts -> {
            mainCurrency = Currencies.main(accounts);
            render();
        });
        viewModel.getRecurringPayments().observe(getViewLifecycleOwner(), bills -> {
            allBills = bills;
            render();
            askForReminders();
        });
        viewModel.getPayments().observe(getViewLifecycleOwner(), payments -> {
            ledger = BillLedger.of(payments);
            render();
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        ((AppCompatActivity) requireActivity()).getSupportActionBar().setTitle("Bills");
    }

    // Reminders need notifications; asked once, here, where they make sense. Settings can ask again.
    private void askForReminders() {
        boolean hasBills = false;
        for (RecurringPayment bill : allBills) {
            hasBills |= !Bills.isTransfer(bill);
        }
        if (hasBills && BillReminderScheduler.shouldAskForPermission(requireContext())) {
            BillReminderScheduler.markAskedForPermission(requireContext());
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    /** Opens on a month; used by the screen tests too. */
    void showMonth(int month) {
        period = Math.min(month, Periods.plus(Periods.current(), MONTHS_AHEAD));
        render();
    }

    private List<RecurringPayment> billsOfTab() {
        List<RecurringPayment> bills = new ArrayList<>();
        for (RecurringPayment bill : allBills) {
            if (Bills.isTransfer(bill) == showTopUps) {
                bills.add(bill);
            }
        }
        return bills;
    }

    private void markAll(boolean paid) {
        Calendar today = Calendar.getInstance();
        for (RecurringPayment bill : billsOfTab()) {
            Bills.Status status = Bills.statusOf(bill, ledger, period, today);
            if (paid && Bills.isOwed(status)) {
                viewModel.markPaid(bill, period);
            } else if (!paid && Bills.isDone(status)) {
                viewModel.markUnpaid(bill, period);
            }
        }
    }

    /** Lists the months missed before this one; picking one opens it. */
    private void pickMissed() {
        List<Bills.Missed> missed = Bills.missedBefore(billsOfTab(), ledger, period, Calendar.getInstance());
        String[] items = new String[missed.size()];
        for (int i = 0; i < missed.size(); i++) {
            Bills.Missed each = missed.get(i);
            items[i] = each.bill.getName() + " · " + Periods.name(each.period) + " · "
                    + Money.format(each.bill.getAmount(), each.bill.getCurrency());
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Unpaid from earlier months")
                .setItems(items, (dialog, which) -> showMonth(missed.get(which).period))
                .setNegativeButton("Close", null)
                .show();
    }

    private void render() {
        if (binding == null) {
            return;
        }
        Calendar today = Calendar.getInstance();
        int current = Periods.current();
        binding.tvMonth.setText(new SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                .format(Periods.firstDay(period).getTime()));
        boolean canGoOn = period < Periods.plus(current, MONTHS_AHEAD);
        binding.btnNextMonth.setEnabled(canGoOn);
        binding.btnNextMonth.setAlpha(canGoOn ? 1f : 0.3f);
        binding.btnThisMonth.setVisibility(period == current ? View.GONE : View.VISIBLE);

        List<RecurringPayment> active = new ArrayList<>();
        List<RecurringPayment> ended = new ArrayList<>();
        CurrencyTotals total = new CurrencyTotals();
        CurrencyTotals left = new CurrencyTotals();
        int done = 0;
        for (RecurringPayment bill : Bills.sortByUrgency(billsOfTab(), ledger, period, today)) {
            Bills.Status status = Bills.statusOf(bill, ledger, period, today);
            if (status == Bills.Status.NOT_STARTED) {
                continue;
            }
            if (status == Bills.Status.ENDED) {
                ended.add(bill);
                continue;
            }
            active.add(bill);
            if (status == Bills.Status.SKIPPED) {
                // Not owed that month, so it counts as done without adding to what was due
                done++;
                continue;
            }
            total.add(bill.getCurrency(), bill.getAmount());
            if (status == Bills.Status.PAID) {
                done++;
            } else {
                left.add(bill.getCurrency(), bill.getAmount());
            }
        }

        CurrencyTotals paid = new CurrencyTotals().addAll(total);
        for (Map.Entry<String, Double> each : left.nonZero(mainCurrency).entrySet()) {
            paid.add(each.getKey(), -each.getValue());
        }
        int count = active.size();
        binding.tvSummaryLabel.setText(period < current ? (showTopUps ? "Not moved" : "Not paid")
                : showTopUps ? "Still to move" : "Still to pay");
        binding.fabAdd.setContentDescription(showTopUps ? "Add fund top-up" : "Add bill");
        binding.tvRemaining.setText(left.format(mainCurrency, mainCurrency));
        Ui.styleTag(binding.tvDoneCount, done + " of " + count + " done", R.color.primary_container,
                R.color.on_primary_container);
        // Amounts in different currencies cannot be added, so with more than one the bar counts bills instead
        Map<String, Double> totals = total.nonZero(mainCurrency);
        if (totals.size() == 1) {
            String only = totals.keySet().iterator().next();
            binding.progressPaid.setProgress((int) Math.round(paid.get(only) * 100 / total.get(only)));
        } else {
            binding.progressPaid.setProgress(count > 0 ? done * 100 / count : 0);
        }
        binding.tvPaidSummary.setText(paidSummary(paid, total));

        fillList(binding.list, active, today, emptyText(period, current));
        fillList(binding.listEnded, ended, today, null);
        binding.headerEnded.setVisibility(ended.isEmpty() ? View.GONE : View.VISIBLE);

        List<Bills.Missed> missed = period == current
                ? Bills.missedBefore(billsOfTab(), ledger, period, today) : new ArrayList<>();
        binding.bannerMissed.setVisibility(missed.isEmpty() ? View.GONE : View.VISIBLE);
        binding.tvMissed.setText(missed.size() == 1 ? "1 unpaid from earlier months"
                : missed.size() + " unpaid from earlier months");

        List<RecurringPayment> pending = Bills.pendingTransfers(billsOfTab(), ledger, period, today);
        CurrencyTotals pendingTotal = new CurrencyTotals();
        for (RecurringPayment bill : pending) {
            pendingTotal.add(bill.getCurrency(), bill.getAmount());
        }
        binding.btnMoveAll.setVisibility(!showTopUps || pending.isEmpty() ? View.GONE : View.VISIBLE);
        binding.btnMoveAll.setText("Top up all " + pendingTotal.format(mainCurrency, mainCurrency));
        int month = period;
        binding.btnMoveAll.setOnClickListener(
                v -> BillActions.confirmMoveAll(requireActivity(), pending, fundNames, month));
    }

    private String emptyText(int month, int current) {
        if (month != current) {
            return showTopUps ? "No top-ups in " + Periods.name(month) + "." : "No bills in " + Periods.name(month) + ".";
        }
        return showTopUps ? "No top-ups yet. Add one with a from and to fund to move money every month."
                : "No bills yet. Add rent, subscriptions or anything else that repeats.";
    }

    /** "₹500 paid of ₹900", or with several currencies "₹500 of ₹900 · AED 0 of AED 300 paid". */
    private String paidSummary(CurrencyTotals paid, CurrencyTotals total) {
        String word = showTopUps ? "moved" : "paid";
        Map<String, Double> totals = total.nonZero(mainCurrency);
        if (totals.size() < 2) {
            return paid.format(mainCurrency, mainCurrency) + " " + word + " of " + total.format(mainCurrency, mainCurrency);
        }
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, Double> each : totals.entrySet()) {
            if (text.length() > 0) {
                text.append(" · ");
            }
            text.append(Money.format(paid.get(each.getKey()), each.getKey())).append(" of ")
                    .append(Money.format(each.getValue(), each.getKey()));
        }
        return text.append(" ").append(word).toString();
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
        Bills.Status status = Bills.statusOf(bill, ledger, period, today);
        boolean paid = status == Bills.Status.PAID;
        boolean done = Bills.isDone(status);
        boolean ended = status == Bills.Status.ENDED;
        int month = period;

        row.getRoot().setBackgroundResource(first && last ? R.drawable.bg_row_single
                : first ? R.drawable.bg_row_top : last ? R.drawable.bg_row_bottom : R.drawable.bg_row_middle);
        row.divider.setVisibility(last ? View.GONE : View.VISIBLE);
        row.tvName.setText(bill.getName());
        row.tvAmount.setText(Money.format(bill.getAmount(), bill.getCurrency()));

        String statusLine = BillText.statusLine(bill, ledger, period, today);
        if (Bills.isTransfer(bill)) {
            String route = TransactionText.fundName(fundNames, bill.getFromFundId()) + " → "
                    + TransactionText.fundName(fundNames, bill.getToFundId());
            statusLine = route + " · " + statusLine;
        }
        row.tvStatus.setText(statusLine);
        row.tvStatus.setTextColor(ContextCompat.getColor(requireContext(),
                status == Bills.Status.LATE ? R.color.alert : R.color.on_surface_variant));

        row.ivCircle.setBackgroundResource(paid ? R.drawable.bg_circle_done : R.drawable.bg_circle_empty);
        if (paid) {
            Ui.tintImage(row.ivCircle, R.drawable.ic_check, R.color.on_primary);
        }
        if (ended || status == Bills.Status.SKIPPED) {
            row.tvName.setTextColor(ContextCompat.getColor(requireContext(), R.color.on_surface_variant));
        }
        if (ended) {
            row.togglePaid.setVisibility(View.INVISIBLE);
        } else {
            row.togglePaid.setContentDescription(done ? "Mark " + bill.getName() + " as unpaid"
                    : "Mark " + bill.getName() + " as paid");
            row.togglePaid.setOnClickListener(v -> {
                if (done) {
                    viewModel.markUnpaid(bill, month);
                } else {
                    viewModel.markPaid(bill, month);
                }
            });
        }

        boolean canMove = Bills.isTransfer(bill) && Bills.isOwed(status)
                && !bill.getFromFundId().equals(bill.getToFundId());
        if (canMove) {
            Ui.styleTag(row.btnMove, "Top up", R.color.transfer_container, R.color.transfer);
            row.btnMove.setOnClickListener(v -> BillActions.confirmMove(requireActivity(), bill, fundNames, month));
        }
        row.getRoot().setOnClickListener(v -> BillDetailsSheet.show(requireActivity(), bill.getId(), month));
        return row.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
