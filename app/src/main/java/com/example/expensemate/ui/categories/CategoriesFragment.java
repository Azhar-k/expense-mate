package com.example.expensemate.ui.categories;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.expensemate.R;
import com.example.expensemate.data.Category;
import com.example.expensemate.databinding.DialogCategoryFormBinding;
import com.example.expensemate.databinding.FragmentCategoriesBinding;
import com.example.expensemate.databinding.ItemCategoryBinding;
import com.example.expensemate.ui.common.TransactionText;
import com.example.expensemate.ui.common.Ui;
import com.example.expensemate.viewmodel.CategoryViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.List;

public class CategoriesFragment extends Fragment {
    private FragmentCategoriesBinding binding;
    private CategoryViewModel viewModel;
    private List<Category> categories = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentCategoriesBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(requireActivity()).get(CategoryViewModel.class);
        binding.fabAdd.setOnClickListener(v -> showForm(null));
        binding.tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                render();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });
        viewModel.getAllCategories().observe(getViewLifecycleOwner(), list -> {
            categories = list;
            render();
        });
    }

    private String selectedType() {
        return binding.tabs.getSelectedTabPosition() == 1 ? "INCOME" : "EXPENSE";
    }

    private void render() {
        binding.list.removeAllViews();
        List<Category> shown = new ArrayList<>();
        for (Category category : categories) {
            if (selectedType().equals(category.getType())) {
                shown.add(category);
            }
        }
        for (int i = 0; i < shown.size(); i++) {
            Category category = shown.get(i);
            boolean first = i == 0;
            boolean last = i == shown.size() - 1;
            ItemCategoryBinding row = ItemCategoryBinding.inflate(getLayoutInflater(), binding.list, false);
            row.getRoot().setBackgroundResource(first && last ? R.drawable.bg_row_single
                    : first ? R.drawable.bg_row_top : last ? R.drawable.bg_row_bottom : R.drawable.bg_row_middle);
            row.divider.setVisibility(last ? View.GONE : View.VISIBLE);
            Ui.styleIconTile(row.ivIcon, TransactionText.categoryIcon(category.getName()), R.color.surface_variant,
                    R.color.on_surface);
            row.tvName.setText(category.getName());
            row.getRoot().setOnClickListener(v -> showForm(category));
            binding.list.addView(row.getRoot());
        }
    }

    private void showForm(@Nullable Category category) {
        DialogCategoryFormBinding form = DialogCategoryFormBinding.inflate(getLayoutInflater());
        String type = category != null ? category.getType() : selectedType();
        form.chipsType.check("INCOME".equals(type) ? R.id.typeIncome : R.id.typeExpense);
        if (category != null) {
            form.etName.setText(category.getName());
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(category == null ? "Add a category" : "Edit category")
                .setView(form.getRoot())
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null);
        if (category != null) {
            builder.setNeutralButton("Delete", (d, which) -> confirmDelete(category));
        }
        AlertDialog dialog = builder.create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = form.etName.getText().toString().trim();
            form.tilName.setError(name.isEmpty() ? "Enter a name" : null);
            if (name.isEmpty()) {
                return;
            }
            String newType = form.chipsType.getCheckedChipId() == R.id.typeIncome ? "INCOME" : "EXPENSE";
            if (category == null) {
                viewModel.insertCategory(new Category(name, newType));
            } else {
                category.setName(name);
                category.setType(newType);
                viewModel.updateCategory(category);
            }
            dialog.dismiss();
        });
    }

    private void confirmDelete(Category category) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete " + category.getName() + "?")
                .setMessage("Transactions already using it keep the name.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    viewModel.deleteCategory(category);
                    Ui.snackbar(requireActivity(), category.getName() + " deleted");
                })
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
