package com.example.expensemate.ui.categories;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
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
import com.example.expensemate.ui.common.BaseDialogHelper;
import com.example.expensemate.viewmodel.CategoryViewModel;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.button.MaterialButton;


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

        AlertDialog dialog = new AlertDialog.Builder(requireContext(), 
                com.google.android.material.R.style.Theme_MaterialComponents_Light_Dialog)
                .setTitle("Add Category")
                .setView(dialogBinding.getRoot())
                .setPositiveButton("Add", null)
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(dialogInterface -> {
            Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            Button negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            
            positiveButton.setTextColor(requireContext().getResources().getColor(R.color.primary));
            negativeButton.setTextColor(requireContext().getResources().getColor(R.color.primary));
            
            positiveButton.setOnClickListener(v -> {
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
            });
        });

        dialog.show();
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
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete Category")
                .setMessage("Are you sure you want to delete this category?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    viewModel.deleteCategory(category);
                    Toast.makeText(requireContext(), "Category deleted", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
} 