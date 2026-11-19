package com.example.expensemate.ui.budgets;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.expensemate.R;
import com.example.expensemate.data.BudgetProgress;
import com.example.expensemate.databinding.FragmentBudgetsBinding;
import com.example.expensemate.databinding.ItemBudgetRowBinding;
import com.example.expensemate.ui.common.ReorderDialog;
import com.example.expensemate.ui.common.Ui;
import com.example.expensemate.util.Currencies;
import com.example.expensemate.util.CurrencyTotals;
import com.example.expensemate.util.Money;
import com.example.expensemate.viewmodel.AccountViewModel;
import com.example.expensemate.viewmodel.BudgetViewModel;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Monthly limits on everyday spending, and how each month is going against them. */
public class BudgetsFragment extends Fragment {
    private FragmentBudgetsBinding binding;
    private BudgetViewModel viewModel;
    private List<BudgetProgress> budgets = new ArrayList<>();
    private String mainCurrency = Currencies.fromLocale();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentBudgetsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        ViewModelProvider provider = new ViewModelProvider(requireActivity());
        viewModel = provider.get(BudgetViewModel.class);

        Ui.addReorderAction(this, this::reorder);
        binding.fabAdd.setOnClickListener(v -> BudgetForm.show(requireActivity(), null));
        binding.btnPrevMonth.setOnClickListener(v -> viewModel.shiftMonth(-1));
        binding.btnNextMonth.setOnClickListener(v -> viewModel.shiftMonth(1));

        viewModel.getMonth().observe(getViewLifecycleOwner(), month -> {
            binding.tvMonth.setText(new SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(new Date(month)));
            boolean current = viewModel.isCurrentMonth();
            binding.btnNextMonth.setEnabled(!current);
            binding.btnNextMonth.setAlpha(current ? 0.3f : 1f);
            binding.tvTotalLabel.setText(current ? "Left to spend this month" : "Left at the end of the month");
        });
        provider.get(AccountViewModel.class).getMainCurrency().observe(getViewLifecycleOwner(), main -> {
            mainCurrency = main;
            render();
        });
        viewModel.getProgress().observe(getViewLifecycleOwner(), list -> {
            budgets = list;
            render();
        });
    }

    private void render() {
        if (binding == null) {
            return;
        }
        ViewGroup list = binding.listBudgets;
        list.removeAllViews();
        CurrencyTotals limit = new CurrencyTotals();
        CurrencyTotals spent = new CurrencyTotals();
        int over = 0;
        for (BudgetProgress progress : budgets) {
            limit.add(progress.budget.getCurrency(), progress.budget.getAmount());
            spent.add(progress.budget.getCurrency(), progress.spent);
            if (progress.isOver()) {
                over++;
            }
        }
        if (budgets.isEmpty()) {
            TextView empty = (TextView) getLayoutInflater().inflate(R.layout.item_list_empty, list, false);
            empty.setText("No budgets yet. Add one to set a monthly limit on categories like Food or Fuel.");
            list.addView(empty);
        }
        for (int i = 0; i < budgets.size(); i++) {
            list.addView(buildRow(list, budgets.get(i), i == 0, i == budgets.size() - 1));
        }

        CurrencyTotals left = new CurrencyTotals().addAll(limit);
        for (BudgetProgress progress : budgets) {
            left.add(progress.budget.getCurrency(), -progress.spent);
        }
        binding.tvTotalLeft.setText(left.format(mainCurrency, mainCurrency));
        binding.tvTotalLeft.setTextColor(requireContext().getColor(left.allNegative() ? R.color.alert : R.color.on_surface));
        String detail = spent.format(mainCurrency, mainCurrency) + " spent of " + limit.format(mainCurrency, mainCurrency);
        if (over > 0) {
            detail += " · " + over + " over";
        }
        binding.tvTotalDetail.setText(budgets.isEmpty() ? "" : detail);
    }

    private View buildRow(ViewGroup list, BudgetProgress progress, boolean first, boolean last) {
        ItemBudgetRowBinding row = ItemBudgetRowBinding.inflate(getLayoutInflater(), list, false);
        row.getRoot().setBackgroundResource(first && last ? R.drawable.bg_row_single
                : first ? R.drawable.bg_row_top : last ? R.drawable.bg_row_bottom : R.drawable.bg_row_middle);
        row.divider.setVisibility(last ? View.GONE : View.VISIBLE);

        String currency = progress.budget.getCurrency();
        row.tvName.setText(progress.budget.getName());
        boolean over = progress.isOver();
        row.tvLeft.setText(over ? Money.format(-progress.left(), currency) + " over"
                : Money.format(progress.left(), currency) + " left");
        row.tvLeft.setTextColor(requireContext().getColor(over ? R.color.alert : R.color.on_surface));
        row.progress.setProgress(progress.percent());
        row.progress.setIndicatorColor(requireContext().getColor(over ? R.color.alert : R.color.primary));
        row.tvSpent.setText(Money.format(progress.spent, currency) + " spent of "
                + Money.format(progress.budget.getAmount(), currency));
        List<String> categories = progress.categories();
        row.tvCategories.setText(categories.isEmpty() ? "No categories yet" : String.join(", ", categories));

        row.getRoot().setOnClickListener(v -> BudgetForm.show(requireActivity(), progress));
        return row.getRoot();
    }

    private void reorder() {
        List<String> names = new ArrayList<>();
        for (BudgetProgress progress : budgets) {
            names.add(progress.budget.getName());
        }
        List<BudgetProgress> shown = new ArrayList<>(budgets);
        ReorderDialog.show(requireContext(), "Order budgets", names, order -> {
            List<Long> ids = new ArrayList<>();
            for (int index : order) {
                ids.add(shown.get(index).budget.getId());
            }
            viewModel.saveOrder(ids);
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
