package com.example.expensemate.ui.home;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.example.expensemate.MainActivity;
import com.example.expensemate.R;
import com.example.expensemate.data.Account;
import com.example.expensemate.data.CategorySum;
import com.example.expensemate.data.RecurringPayment;
import com.example.expensemate.data.SummaryTotals;
import com.example.expensemate.databinding.FragmentHomeBinding;
import com.example.expensemate.databinding.ItemBreakdownRowBinding;
import com.example.expensemate.databinding.ItemComingUpBinding;
import com.example.expensemate.ui.activity.TransactionFormSheet;
import com.example.expensemate.ui.bills.BillText;
import com.example.expensemate.util.Bills;
import com.example.expensemate.util.Currencies;
import com.example.expensemate.util.Money;
import com.example.expensemate.viewmodel.AccountViewModel;
import com.example.expensemate.viewmodel.RecurringPaymentsViewModel;
import com.example.expensemate.viewmodel.TransactionViewModel;
import com.google.android.material.chip.Chip;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class HomeFragment extends Fragment {
    private static final int COMING_UP_LIMIT = 3;
    private static final int[] CHART_COLORS = { R.color.chart_1, R.color.chart_2, R.color.chart_3, R.color.chart_4 };

    private FragmentHomeBinding binding;
    private TransactionViewModel viewModel;
    private List<CategorySum> expenses = new ArrayList<>();
    private List<CategorySum> income = new ArrayList<>();
    private List<RecurringPayment> bills = new ArrayList<>();
    private List<Account> accounts = new ArrayList<>();
    private List<SummaryTotals> currencyTotals = new ArrayList<>();
    private final SimpleDateFormat monthFormat = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
    private Menu menu;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        ViewModelProvider provider = new ViewModelProvider(requireActivity());
        viewModel = provider.get(TransactionViewModel.class);

        requireActivity().addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu created, @NonNull MenuInflater inflater) {
                inflater.inflate(R.menu.menu_home, created);
                menu = created;
                updateMonthControls();
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem item) {
                int id = item.getItemId();
                if (id == R.id.action_prev_month) {
                    moveMonth(-1);
                    return true;
                }
                if (id == R.id.action_next_month) {
                    moveMonth(1);
                    return true;
                }
                if (id == R.id.action_settings) {
                    NavHostFragment.findNavController(HomeFragment.this).navigate(R.id.navigation_settings);
                    return true;
                }
                return false;
            }
        }, getViewLifecycleOwner(), Lifecycle.State.RESUMED);

        binding.fabAdd.setOnClickListener(v -> TransactionFormSheet.showAdd(requireActivity()));
        binding.bannerReview.setOnClickListener(v -> {
            viewModel.showNeedsReview();
            ((MainActivity) requireActivity()).openTab(R.id.navigation_activity);
        });
        binding.btnAllBills.setOnClickListener(v -> ((MainActivity) requireActivity()).openTab(R.id.navigation_bills));

        binding.chipsBreakdown.check(viewModel.isHomeShowingExpenses() ? R.id.chipExpenses : R.id.chipIncome);
        binding.chipsBreakdown.setOnCheckedStateChangeListener((group, checkedIds) -> {
            viewModel.setHomeShowingExpenses(checkedIds.contains(R.id.chipExpenses));
            renderBreakdown();
        });

        AccountViewModel accountViewModel = provider.get(AccountViewModel.class);
        accountViewModel.getAllAccounts().observe(getViewLifecycleOwner(), list -> {
            accounts = list;
            buildAccountChips();
            renderTotals(currencyTotals);
        });
        accountViewModel.getDefaultAccount().observe(getViewLifecycleOwner(), account -> {
            if (account != null) {
                viewModel.applyDefaultHomeAccount(account.getId());
                buildAccountChips();
            }
        });
        viewModel.getHomeTotals().observe(getViewLifecycleOwner(), list -> renderTotals(list != null ? list : new ArrayList<>()));
        viewModel.getHomeExpenses().observe(getViewLifecycleOwner(), list -> {
            expenses = list;
            renderBreakdown();
        });
        viewModel.getHomeIncome().observe(getViewLifecycleOwner(), list -> {
            income = list;
            renderBreakdown();
        });
        viewModel.getNeedsReviewCount().observe(getViewLifecycleOwner(), count -> {
            binding.bannerReview.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
            binding.tvReview.setText(count == 1 ? "1 transaction needs a category"
                    : count + " transactions need a category");
        });
        provider.get(RecurringPaymentsViewModel.class).getRecurringPayments().observe(getViewLifecycleOwner(), list -> {
            bills = list;
            renderComingUp();
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        updateMonthControls();
    }

    private void moveMonth(int months) {
        viewModel.moveHomeMonth(months);
        updateMonthControls();
        renderComingUp();
    }

    private boolean showingCurrentMonth() {
        Calendar shown = viewModel.getHomeMonth();
        Calendar now = Calendar.getInstance();
        return shown.get(Calendar.YEAR) == now.get(Calendar.YEAR) && shown.get(Calendar.MONTH) == now.get(Calendar.MONTH);
    }

    private void updateMonthControls() {
        AppCompatActivity activity = (AppCompatActivity) getActivity();
        if (activity != null && activity.getSupportActionBar() != null && isResumed()) {
            activity.getSupportActionBar().setTitle(monthFormat.format(viewModel.getHomeMonth().getTime()));
        }
        if (menu != null) {
            MenuItem next = menu.findItem(R.id.action_next_month);
            if (next != null) {
                boolean enabled = !showingCurrentMonth();
                next.setEnabled(enabled);
                next.getIcon().mutate().setAlpha(enabled ? 255 : 70);
            }
        }
    }

    private void buildAccountChips() {
        binding.chipsAccount.removeAllViews();
        addAccountChip("All accounts", null);
        for (Account account : accounts) {
            addAccountChip(account.getName(), account.getId());
        }
    }

    private void addAccountChip(String label, Long accountId) {
        Chip chip = new Chip(requireContext(), null, com.google.android.material.R.attr.chipStyle);
        chip.setText(label);
        chip.setCheckable(true);
        chip.setCheckedIconVisible(false);
        Long selected = viewModel.getHomeAccountId();
        chip.setChecked(accountId == null ? selected == null : accountId.equals(selected));
        if (chip.isChecked()) {
            // The selected account can be far along the row, so bring it into view
            chip.post(() -> {
                if (binding != null) {
                    binding.scrollAccounts.smoothScrollTo(chip.getLeft(), 0);
                }
            });
        }
        chip.setOnClickListener(v -> {
            chip.setChecked(true);
            viewModel.setHomeAccount(accountId);
        });
        binding.chipsAccount.addView(chip);
    }

    private void renderTotals(List<SummaryTotals> perCurrency) {
        currencyTotals = perCurrency;
        String currency = pickCurrency(perCurrency);
        SummaryTotals totals = new SummaryTotals();
        totals.currency = currency;
        for (SummaryTotals each : perCurrency) {
            if (currency.equals(each.currency)) {
                totals = each;
            }
        }
        double left = totals.left();
        boolean singleAccount = viewModel.getHomeAccountId() != null;
        String month = showingCurrentMonth() ? "this month"
                : "in " + new SimpleDateFormat("MMMM", Locale.getDefault()).format(viewModel.getHomeMonth().getTime());
        binding.tvLeftLabel.setText((left >= 0 ? "Left " : singleAccount ? "Down " : "Overspent ") + month);
        binding.tvLeft.setText(Money.format(Math.abs(left), currency));
        binding.tvLeft.setTextColor(requireContext().getColor(left < 0 ? R.color.alert : R.color.on_surface));

        double moneyIn = totals.income + totals.movedIn;
        double moneyOut = totals.expense + totals.movedOut;
        int percent = moneyIn > 0 ? (int) Math.min(100, Math.round(moneyOut * 100 / moneyIn)) : moneyOut > 0 ? 100 : 0;
        binding.progressSpent.setProgress(percent);
        binding.progressSpent.setIndicatorColor(requireContext().getColor(left < 0 ? R.color.alert : R.color.primary));
        binding.tvSpent.setText("Spent " + Money.format(totals.expense, currency));
        binding.tvIncome.setText("Income " + Money.format(totals.income, currency));

        // A single account's balance also changes through transfers, so say how much moved
        boolean showMoved = singleAccount && (totals.movedIn > 0 || totals.movedOut > 0);
        binding.tvMoved.setVisibility(showMoved ? View.VISIBLE : View.GONE);
        binding.tvMoved.setText("Moved in " + Money.format(totals.movedIn, currency) + " · Moved out "
                + Money.format(totals.movedOut, currency));
    }

    /**
     * The currency the summary card shows: the one picked before while it still has money this month,
     * else the main currency, else whichever has money. More than one gets a row of chips to switch.
     */
    private String pickCurrency(List<SummaryTotals> perCurrency) {
        String main = Currencies.main(accounts);
        List<String> codes = new ArrayList<>();
        for (SummaryTotals each : perCurrency) {
            if (each.currency != null) {
                codes.add(each.currency);
            }
        }
        codes.sort(Currencies.mainFirst(main));
        String shown = viewModel.getHomeCurrency();
        if (shown == null || !codes.contains(shown)) {
            shown = codes.isEmpty() || codes.contains(main) ? main : codes.get(0);
        }
        viewModel.setHomeCurrency(shown);
        buildCurrencyChips(codes, shown);
        return shown;
    }

    private void buildCurrencyChips(List<String> codes, String shown) {
        binding.chipsCurrency.removeAllViews();
        binding.chipsCurrency.setVisibility(codes.size() > 1 ? View.VISIBLE : View.GONE);
        if (codes.size() < 2) {
            return;
        }
        for (String code : codes) {
            Chip chip = new Chip(requireContext(), null, com.google.android.material.R.attr.chipStyle);
            chip.setText(code);
            chip.setContentDescription("Show " + Currencies.displayName(code));
            chip.setCheckable(true);
            chip.setCheckedIconVisible(false);
            chip.setChecked(code.equals(shown));
            chip.setOnClickListener(v -> {
                chip.setChecked(true);
                viewModel.setHomeCurrency(code);
            });
            binding.chipsCurrency.addView(chip);
        }
    }

    private void renderBreakdown() {
        boolean showingExpenses = viewModel.isHomeShowingExpenses();
        List<CategorySum> sums = showingExpenses ? expenses : income;
        binding.breakdownRows.removeAllViews();
        binding.tvBreakdownEmpty.setVisibility(sums.isEmpty() ? View.VISIBLE : View.GONE);
        binding.stackedBar.setVisibility(sums.isEmpty() ? View.GONE : View.VISIBLE);

        double total = 0;
        for (CategorySum sum : sums) {
            total += sum.getTotal();
        }

        // The four largest get their own colours; everything smaller shares the last segment
        int segments = Math.min(sums.size(), CHART_COLORS.length + (sums.size() > CHART_COLORS.length ? 1 : 0));
        double[] values = new double[segments];
        int[] colors = new int[segments];
        LayoutInflater inflater = getLayoutInflater();
        for (int i = 0; i < sums.size(); i++) {
            CategorySum sum = sums.get(i);
            int colorRes = i < CHART_COLORS.length ? CHART_COLORS[i] : R.color.chart_other;
            int color = requireContext().getColor(colorRes);
            int segment = Math.min(i, CHART_COLORS.length);
            values[segment] += sum.getTotal();
            colors[segment] = color;

            ItemBreakdownRowBinding row = ItemBreakdownRowBinding.inflate(inflater, binding.breakdownRows, false);
            row.dot.setBackgroundTintList(ColorStateList.valueOf(color));
            row.tvName.setText(sum.getCategory());
            row.tvPercent.setText(Math.round(sum.getTotal() * 100 / total) + "%");
            row.tvAmount.setText(Money.format(sum.getTotal(), viewModel.getHomeCurrency()));
            row.getRoot().setOnClickListener(v -> {
                viewModel.showHomeCategory(sum.getCategory(), showingExpenses);
                ((MainActivity) requireActivity()).openTab(R.id.navigation_activity);
            });
            binding.breakdownRows.addView(row.getRoot());
        }
        binding.stackedBar.setSegments(values, colors);
    }

    private void renderComingUp() {
        binding.comingUpRows.removeAllViews();
        Calendar today = Calendar.getInstance();
        int shown = 0;
        if (showingCurrentMonth()) {
            for (RecurringPayment bill : Bills.sortByUrgency(bills, today)) {
                Bills.Status status = Bills.statusOf(bill, today);
                if (shown == COMING_UP_LIMIT || status == Bills.Status.PAID || status == Bills.Status.ENDED) {
                    break;
                }
                ItemComingUpBinding row = ItemComingUpBinding.inflate(getLayoutInflater(), binding.comingUpRows, false);
                row.tvName.setText(bill.getName());
                row.tvAmount.setText(Money.format(bill.getAmount(), bill.getCurrency()));
                BillText.styleStatusTag(row.tvTag, bill, today);
                binding.comingUpRows.addView(row.getRoot());
                shown++;
            }
        }
        binding.cardComingUp.setVisibility(shown > 0 ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        menu = null;
        binding = null;
    }
}
