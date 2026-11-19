package com.example.expensemate.ui.budgets;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.expensemate.R;
import com.example.expensemate.data.Budget;
import com.example.expensemate.data.BudgetProgress;
import com.example.expensemate.data.Category;
import com.example.expensemate.databinding.DialogBudgetFormBinding;
import com.example.expensemate.ui.common.CurrencyField;
import com.example.expensemate.ui.common.FormSheet;
import com.example.expensemate.ui.common.Ui;
import com.example.expensemate.util.Currencies;
import com.example.expensemate.util.Money;
import com.example.expensemate.viewmodel.AccountViewModel;
import com.example.expensemate.viewmodel.BudgetViewModel;
import com.example.expensemate.viewmodel.CategoryViewModel;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Adds a budget, or changes or deletes one. */
final class BudgetForm {
    private BudgetForm() {
    }

    static void show(FragmentActivity activity, @Nullable BudgetProgress editing) {
        DialogBudgetFormBinding form = DialogBudgetFormBinding.inflate(LayoutInflater.from(activity));
        Budget budget = editing != null ? editing.budget : null;
        Set<String> chosen = new HashSet<>(editing != null ? editing.categories() : new ArrayList<>());

        if (budget != null) {
            form.etName.setText(budget.getName());
            form.etAmount.setText(Money.plain(budget.getAmount()));
        }
        Ui.observeOnce(new ViewModelProvider(activity).get(AccountViewModel.class).getAllAccounts(), activity, list -> {
            String main = Currencies.main(list);
            CurrencyField.setUp(form.etCurrency, budget != null ? budget.getCurrency() : main, main, Currencies.inUse(list));
        });
        Ui.observeOnce(new ViewModelProvider(activity).get(CategoryViewModel.class).getCategoriesByType("EXPENSE"),
                activity, categories -> addChips(form, categories, chosen));

        FormSheet sheet = FormSheet.create(activity, budget == null ? "Add a budget" : "Edit " + budget.getName(),
                form.getRoot());
        BudgetViewModel viewModel = new ViewModelProvider(activity).get(BudgetViewModel.class);
        sheet.onSave(() -> {
            String name = form.etName.getText().toString().trim();
            Double amount = Money.parse(form.etAmount.getText().toString());
            String currency = CurrencyField.read(form.etCurrency);
            List<String> categories = checkedCategories(form);
            form.tilName.setError(name.isEmpty() ? "Enter a name" : null);
            form.tilAmount.setError(amount == null || amount <= 0 ? "Enter an amount above zero" : null);
            form.tilCurrency.setError(currency == null ? "Pick a currency" : null);
            form.tvCategoriesLabel.setText(categories.isEmpty() ? "Pick at least one category" : "Categories it covers");
            form.tvCategoriesLabel.setTextColor(activity.getColor(categories.isEmpty() ? R.color.alert
                    : R.color.on_surface_variant));
            if (name.isEmpty() || amount == null || amount <= 0 || currency == null || categories.isEmpty()) {
                return;
            }
            Budget saved = budget != null ? budget : new Budget();
            saved.setName(name);
            saved.setAmount(amount);
            saved.setCurrency(currency);
            if (budget != null) {
                viewModel.update(saved, categories);
            } else {
                viewModel.add(saved, categories);
            }
            sheet.dismiss();
        });
        if (budget != null) {
            sheet.onDelete(() -> new MaterialAlertDialogBuilder(activity)
                    .setTitle("Delete " + budget.getName() + "?")
                    .setMessage("Your transactions stay as they are; only the limit goes.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Delete", (dialog, which) -> {
                        viewModel.delete(budget);
                        Ui.snackbar(activity, budget.getName() + " deleted");
                    })
                    .show());
        }
        sheet.show();
    }

    // The default category is where unsorted spending lands, so it can be budgeted like any other
    private static void addChips(DialogBudgetFormBinding form, List<Category> categories, Set<String> chosen) {
        Context context = new ContextThemeWrapper(form.getRoot().getContext(), R.style.ThemeOverlay_ExpenseMate_FormChips);
        Set<String> shown = new HashSet<>();
        for (Category category : categories) {
            shown.add(category.getName());
            form.chipsCategories.addView(chip(context, category.getName(), chosen.contains(category.getName())));
        }
        // A category deleted since keeps counting until it is unticked
        for (String name : chosen) {
            if (!shown.contains(name)) {
                form.chipsCategories.addView(chip(context, name, true));
            }
        }
    }

    private static Chip chip(Context context, String name, boolean checked) {
        Chip chip = new Chip(context, null, com.google.android.material.R.attr.chipStyle);
        chip.setId(View.generateViewId());
        chip.setText(name);
        chip.setCheckable(true);
        chip.setChecked(checked);
        return chip;
    }

    private static List<String> checkedCategories(DialogBudgetFormBinding form) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < form.chipsCategories.getChildCount(); i++) {
            Chip chip = (Chip) form.chipsCategories.getChildAt(i);
            if (chip.isChecked()) {
                names.add(chip.getText().toString());
            }
        }
        return names;
    }
}
