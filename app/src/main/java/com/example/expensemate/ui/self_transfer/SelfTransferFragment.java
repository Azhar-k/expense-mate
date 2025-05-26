package com.example.expensemate.ui.self_transfer;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.example.expensemate.R;
import com.example.expensemate.data.Account;
import com.example.expensemate.data.Category;
import com.example.expensemate.data.Transaction;
import com.example.expensemate.databinding.FragmentSelfTransferBinding;
import com.example.expensemate.viewmodel.AccountViewModel;
import com.example.expensemate.viewmodel.CategoryViewModel;
import com.example.expensemate.viewmodel.TransactionViewModel;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SelfTransferFragment extends Fragment {
    private FragmentSelfTransferBinding binding;
    private TransactionViewModel transactionViewModel;
    private AccountViewModel accountViewModel;
    private CategoryViewModel categoryViewModel;
    private Calendar calendar;
    private SimpleDateFormat dateFormat;
    private Account selectedFromAccount;
    private Account selectedToAccount;
    private Category selectedCategory;
    private List<Account> accounts;
    private List<Category> categories;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentSelfTransferBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        // Initialize ViewModels
        transactionViewModel = new ViewModelProvider(requireActivity()).get(TransactionViewModel.class);
        accountViewModel = new ViewModelProvider(requireActivity()).get(AccountViewModel.class);
        categoryViewModel = new ViewModelProvider(requireActivity()).get(CategoryViewModel.class);

        // Initialize date picker
        calendar = Calendar.getInstance();
        dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
        binding.etDate.setText(dateFormat.format(calendar.getTime()));

        // Setup date picker
        binding.etDate.setOnClickListener(v -> showDatePicker());

        // Setup account dropdowns
        setupAccountDropdowns();

        // Setup category dropdown
        setupCategoryDropdown();

        // Setup transfer button
        binding.btnTransfer.setOnClickListener(v -> performTransfer());

        return root;
    }

    private void setupAccountDropdowns() {
        accountViewModel.getAllAccounts().observe(getViewLifecycleOwner(), accountList -> {
            accounts = accountList;
            List<String> accountNames = new ArrayList<>();
            for (Account account : accounts) {
                accountNames.add(account.getName());
            }

            ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                accountNames
            ) {
                @Override
                public View getView(int position, View convertView, ViewGroup parent) {
                    View view = super.getView(position, convertView, parent);
                    TextView text = (TextView) view.findViewById(android.R.id.text1);
                    text.setTextColor(requireContext().getResources().getColor(R.color.black));
                    return view;
                }
            };

            binding.etFromAccount.setAdapter(adapter);
            binding.etToAccount.setAdapter(adapter);

            binding.etFromAccount.setOnItemClickListener((parent, view, position, id) -> {
                String selectedName = (String) parent.getItemAtPosition(position);
                for (Account account : accounts) {
                    if (account.getName().equals(selectedName)) {
                        selectedFromAccount = account;
                        break;
                    }
                }
            });

            binding.etToAccount.setOnItemClickListener((parent, view, position, id) -> {
                String selectedName = (String) parent.getItemAtPosition(position);
                for (Account account : accounts) {
                    if (account.getName().equals(selectedName)) {
                        selectedToAccount = account;
                        break;
                    }
                }
            });
        });
        // Show dropdown when clicked
        binding.etFromAccount.setOnClickListener(v -> {
            binding.etFromAccount.showDropDown();
        });
        binding.etToAccount.setOnClickListener(v -> {
            binding.etToAccount.showDropDown();
        });
        binding.etFromAccount.setDropDownBackgroundResource(android.R.color.white);
        binding.etToAccount.setDropDownBackgroundResource(android.R.color.white);
    }

    private void setupCategoryDropdown() {
        categoryViewModel.getCategoriesByType("EXPENSE").observe(getViewLifecycleOwner(), categoryList -> {
            categories = categoryList;
            List<String> categoryNames = new ArrayList<>();
            for (Category category : categories) {
                categoryNames.add(category.getName());
            }

            ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                categoryNames
            ) {
                @Override
                public View getView(int position, View convertView, ViewGroup parent) {
                    View view = super.getView(position, convertView, parent);
                    TextView text = (TextView) view.findViewById(android.R.id.text1);
                    text.setTextColor(requireContext().getResources().getColor(R.color.black));
                    return view;
                }
            };

            binding.etCategory.setAdapter(adapter);
            binding.etCategory.setOnItemClickListener((parent, view, position, id) -> {
                String selectedName = (String) parent.getItemAtPosition(position);
                for (Category category : categories) {
                    if (category.getName().equals(selectedName)) {
                        selectedCategory = category;
                        break;
                    }
                }
            });

            // Show dropdown when clicked
            binding.etCategory.setOnClickListener(v -> {
                binding.etCategory.showDropDown();
                binding.etCategory.requestFocus();
            });
            binding.etCategory.setDropDownBackgroundResource(android.R.color.white);

            // Set default category
            if (!categoryNames.isEmpty()) {
                selectedCategory = categories.get(0);
                binding.etCategory.setText(selectedCategory.getName(), false);
            }
        });
    }

    private void showDatePicker() {
        DatePickerDialog datePickerDialog = new DatePickerDialog(
            requireContext(),
            (view, year, month, dayOfMonth) -> {
                calendar.set(Calendar.YEAR, year);
                calendar.set(Calendar.MONTH, month);
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                binding.etDate.setText(dateFormat.format(calendar.getTime()));
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        );
        datePickerDialog.show();
    }

    private void performTransfer() {
        String amountStr = binding.etAmount.getText().toString();
        String description = binding.etDescription.getText().toString();

        if (amountStr.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter amount", Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedFromAccount == null || selectedToAccount == null) {
            Toast.makeText(requireContext(), "Please select both accounts", Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedFromAccount.getId() == selectedToAccount.getId()) {
            Toast.makeText(requireContext(), "Please select different accounts", Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedCategory == null) {
            Toast.makeText(requireContext(), "Please select a category", Toast.LENGTH_SHORT).show();
            return;
        }

        double amount = Double.parseDouble(amountStr);
        Date date = calendar.getTime();

        // Create debit transaction for from account
        Transaction debitTransaction = new Transaction(
            amount,
            description,
            date,
            "DEBIT",
            "", // Empty receiver name for self transfer
            "", // Empty SMS body
            ""  // Empty SMS sender
        );
        debitTransaction.setCategory(selectedCategory.getName());
        debitTransaction.setAccountId(selectedFromAccount.getId());

        // Create credit transaction for to account
        Transaction creditTransaction = new Transaction(
            amount,
            description,
            date,
            "CREDIT",
            "", // Empty receiver name for self transfer
            "", // Empty SMS body
            ""  // Empty SMS sender
        );
        creditTransaction.setCategory(selectedCategory.getName());
        creditTransaction.setAccountId(selectedToAccount.getId());

        // Insert both transactions
        transactionViewModel.insertTransaction(debitTransaction);
        transactionViewModel.insertTransaction(creditTransaction);

        Toast.makeText(requireContext(), "Transfer completed successfully", Toast.LENGTH_SHORT).show();
        clearFields();
    }

    private void clearFields() {
        binding.etAmount.setText("");
        binding.etDescription.setText("");
        binding.etFromAccount.setText("");
        binding.etToAccount.setText("");
        calendar = Calendar.getInstance();
        binding.etDate.setText(dateFormat.format(calendar.getTime()));
        selectedFromAccount = null;
        selectedToAccount = null;
        // Keep the selected category
        if (selectedCategory != null) {
            binding.etCategory.setText(selectedCategory.getName(), false);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
} 