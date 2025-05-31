package com.example.expensemate.ui.categories;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.expensemate.R;
import com.example.expensemate.data.BackupDataLoader;
import com.example.expensemate.data.Category;
import com.example.expensemate.databinding.DialogEditCategoryBinding;
import com.example.expensemate.databinding.FragmentCategoriesBinding;
import com.example.expensemate.viewmodel.CategoryViewModel;
import com.example.expensemate.ui.common.BaseDialogHelper;

public class CategoriesFragment extends Fragment {
    private FragmentCategoriesBinding binding;
    private CategoryViewModel viewModel;
    private CategoryAdapter adapter;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentCategoriesBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        // Initialize ViewModel
        viewModel = new ViewModelProvider(requireActivity()).get(CategoryViewModel.class);

        // Setup RecyclerView
        adapter = new CategoryAdapter(new CategoryAdapter.CategoryDiffCallback(), this::showEditDialog, this);
        binding.rvCategories.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvCategories.setAdapter(adapter);

        // Observe categories
        viewModel.getAllCategories().observe(getViewLifecycleOwner(), categories -> {
            adapter.submitList(categories);
        });

        // Setup FAB
        binding.fabAddCategory.setOnClickListener(v -> showAddDialog());
        return root;
    }

    private void loadBackup() {
        BackupDataLoader loader = new BackupDataLoader(requireActivity());
        loader.loadBackupData();
    }

    private void showAddDialog() {
        DialogEditCategoryBinding dialogBinding = DialogEditCategoryBinding.inflate(getLayoutInflater());

        // Set up category type dropdown
        String[] categoryTypes = {"EXPENSE", "INCOME"};
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                categoryTypes
        );
        dialogBinding.etCategoryType.setAdapter(typeAdapter);
        dialogBinding.etCategoryType.setText("EXPENSE", false);
        dialogBinding.etCategoryType.setOnClickListener(v -> dialogBinding.etCategoryType.showDropDown());

        BaseDialogHelper dialogHelper = new BaseDialogHelper(
                requireContext(),
                "Add Category",
                dialogBinding.getRoot(),
                "Add",
                "Cancel",
                new BaseDialogHelper.OnDialogButtonClickListener() {
                    @Override
                    public void onPositiveButtonClick(AlertDialog dialog) {
                        String name = dialogBinding.etCategoryName.getText().toString().trim();
                        String type = dialogBinding.etCategoryType.getText().toString();

                        if (name.isEmpty()) {
                            Toast.makeText(requireContext(), "Please enter category name", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        if (name != null && name.equalsIgnoreCase("backup")) {
                            loadBackup();
                            Toast.makeText(requireContext(), "Backup loaded", Toast.LENGTH_SHORT).show();
                        } else {
                            Category category = new Category(name, type);
                            viewModel.insertCategory(category);
                            Toast.makeText(requireContext(), "Category added", Toast.LENGTH_SHORT).show();
                        }
                        dialog.dismiss();
                    }

                    @Override
                    public void onNegativeButtonClick(AlertDialog dialog) {
                        dialog.dismiss();
                    }
                }
        );

        dialogHelper.create().show();
    }

    private void showEditDialog(Category category) {
        DialogEditCategoryBinding dialogBinding = DialogEditCategoryBinding.inflate(getLayoutInflater());

        // Pre-fill the fields
        dialogBinding.etCategoryName.setText(category.getName());
        dialogBinding.etCategoryType.setText(category.getType());

        // Set up category type dropdown
        String[] categoryTypes = {"EXPENSE", "INCOME"};
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                categoryTypes
        );
        dialogBinding.etCategoryType.setAdapter(typeAdapter);
        dialogBinding.etCategoryType.setOnClickListener(v -> dialogBinding.etCategoryType.showDropDown());

        BaseDialogHelper dialogHelper = new BaseDialogHelper(
                requireContext(),
                "Edit Category",
                dialogBinding.getRoot(),
                "Save",
                "Cancel",
                new BaseDialogHelper.OnDialogButtonClickListener() {
                    @Override
                    public void onPositiveButtonClick(AlertDialog dialog) {
                        String name = dialogBinding.etCategoryName.getText().toString();
                        String type = dialogBinding.etCategoryType.getText().toString();

                        if (name.isEmpty()) {
                            Toast.makeText(requireContext(), "Please enter category name", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        category.setName(name);
                        category.setType(type);
                        viewModel.updateCategory(category);
                        dialog.dismiss();
                    }

                    @Override
                    public void onNegativeButtonClick(AlertDialog dialog) {
                        dialog.dismiss();
                    }
                }
        );

        dialogHelper.create().show();
    }

    public void showDeleteConfirmationDialog(Category category) {
        BaseDialogHelper dialogHelper = new BaseDialogHelper(
                requireContext(),
                "Delete Category",
                null,
                "Delete",
                "Cancel",
                new BaseDialogHelper.OnDialogButtonClickListener() {
                    @Override
                    public void onPositiveButtonClick(AlertDialog dialog) {
                        viewModel.deleteCategory(category);
                        Toast.makeText(requireContext(), "Category deleted", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    }

                    @Override
                    public void onNegativeButtonClick(AlertDialog dialog) {
                        dialog.dismiss();
                    }
                }
        );

        dialogHelper.setMessage("Are you sure you want to delete this category?");
        dialogHelper.create().show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
} 