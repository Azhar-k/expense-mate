package com.example.expensemate.ui.transactions;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.expensemate.R;
import com.example.expensemate.databinding.FragmentTransactionsBinding;
import com.example.expensemate.viewmodel.TransactionViewModel;
import com.example.expensemate.viewmodel.AccountViewModel;
import com.example.expensemate.viewmodel.CategoryViewModel;
import com.example.expensemate.viewmodel.RecurringPaymentsViewModel;
import com.example.expensemate.data.Account;
import com.example.expensemate.data.Category;
import com.example.expensemate.data.Transaction;
import com.example.expensemate.data.RecurringPayment;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.switchmaterial.SwitchMaterial;
import android.widget.ImageButton;
import android.app.DatePickerDialog;
import com.google.android.material.datepicker.MaterialDatePicker;
import java.util.TimeZone;

public class TransactionsFragment extends Fragment {
    private static final String TAG = "TransactionsFragment";
    private FragmentTransactionsBinding binding;
    private TransactionViewModel viewModel;
    private AccountViewModel accountViewModel;
    private CategoryViewModel categoryViewModel;
    private TransactionsAdapter adapter;
    private Calendar currentPeriod;
    private List<Account> accounts = new ArrayList<>();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentTransactionsBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        // Initialize ViewModels
        viewModel = new ViewModelProvider(requireActivity()).get(TransactionViewModel.class);
        accountViewModel = new ViewModelProvider(requireActivity()).get(AccountViewModel.class);
        categoryViewModel = new ViewModelProvider(requireActivity()).get(CategoryViewModel.class);
        Log.d(TAG, "ViewModels initialized");

        // Initialize current period
        currentPeriod = Calendar.getInstance();
        updatePeriodDisplay();
        updateSelectedPeriod();

        // Set up month navigation
        binding.btnPrevMonth.setOnClickListener(v -> {
            Log.d(TAG, "Previous month button clicked");
            currentPeriod.add(Calendar.MONTH, -1);
            updatePeriodDisplay();
            updateSelectedPeriod();
        });

        binding.btnNextMonth.setOnClickListener(v -> {
            Log.d(TAG, "Next month button clicked");
            currentPeriod.add(Calendar.MONTH, 1);
            updatePeriodDisplay();
            updateSelectedPeriod();
        });

        // Setup RecyclerView
        adapter = new TransactionsAdapter(viewModel, requireContext());
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.recyclerView.setAdapter(adapter);
        Log.d(TAG, "RecyclerView and adapter setup completed");

        // Set up FAB
        binding.fabAddTransaction.setOnClickListener(v -> adapter.showAddTransactionDialog());

        // Set up filter FAB
        binding.fabFilter.setOnClickListener(v -> showFilterBottomSheet());

        // Set up account dropdown
        accountViewModel.getAllAccounts().observe(getViewLifecycleOwner(), accountList -> {
            accounts = accountList;
            List<String> accountNames = new ArrayList<>();
            // Add "All" as the first option
            accountNames.add("All");
            for (Account account : accounts) {
                accountNames.add(account.getName());
            }
            ArrayAdapter<String> accountAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                accountNames
            ){
                @Override
                public View getView(int position, View convertView, ViewGroup parent) {
                    View view = super.getView(position, convertView, parent);
                    TextView text = (TextView) view.findViewById(android.R.id.text1);
                    text.setTextColor(requireContext().getResources().getColor(R.color.black));
                    return view;
                }
            };
            binding.accountDropdown.setAdapter(accountAdapter);
            binding.accountDropdown.setDropDownBackgroundResource(android.R.color.white);

            // Set "All" as default selection
            binding.accountDropdown.setText("All", false);
            viewModel.setSelectedAccount(null); // null means no account filter
        });

        // Set up account selection listener
        binding.accountDropdown.setOnItemClickListener((parent, view, position, id) -> {
            String selectedAccountName = parent.getItemAtPosition(position).toString();
            if (selectedAccountName.equals("All")) {
                viewModel.setSelectedAccount(null); // null means no account filter
            } else {
                for (Account account : accounts) {
                    if (account.getName().equals(selectedAccountName)) {
                        viewModel.setSelectedAccount(account.getId());
                        break;
                    }
                }
            }
        });

        // Observe filtered transactions
        viewModel.getFilteredTransactions().observe(getViewLifecycleOwner(), transactions -> {
            Log.d(TAG, "Filtered transactions updated. Count: " + (transactions != null ? transactions.size() : 0));
            adapter.submitList(transactions);
            // Show/hide empty state
            binding.tvEmptyState.setVisibility(transactions != null && transactions.isEmpty() ? View.VISIBLE : View.GONE);
        });

        return root;
    }

    private void updatePeriodDisplay() {
        SimpleDateFormat monthYearFormat = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
        String periodText = monthYearFormat.format(currentPeriod.getTime());
        binding.tvPeriod.setText(periodText);
        Log.d(TAG, "Period display updated to: " + periodText);
    }

    private void updateSelectedPeriod() {
        SimpleDateFormat monthFormat = new SimpleDateFormat("MM", Locale.getDefault());
        SimpleDateFormat yearFormat = new SimpleDateFormat("yyyy", Locale.getDefault());
        String month = monthFormat.format(currentPeriod.getTime());
        String year = yearFormat.format(currentPeriod.getTime());
        Log.d(TAG, "Updating selected period to: " + month + "/" + year);
        viewModel.setSelectedMonthYear(month, year);
    }

    private void setupFilters() {
        // Set up filter FAB
        binding.fabFilter.setOnClickListener(v -> showFilterBottomSheet());
    }

    private void showFilterBottomSheet() {
        View bottomSheetView = getLayoutInflater().inflate(R.layout.bottom_sheet_filter, null);
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(requireContext());
        bottomSheetDialog.setContentView(bottomSheetView);

        // Initialize views
        TextInputEditText etFromDate = bottomSheetView.findViewById(R.id.etFromDate);
        TextInputEditText etToDate = bottomSheetView.findViewById(R.id.etToDate);
        AutoCompleteTextView etTransactionType = bottomSheetView.findViewById(R.id.etTransactionType);
        TextInputEditText etDescription = bottomSheetView.findViewById(R.id.etDescription);
        TextInputEditText etReceiver = bottomSheetView.findViewById(R.id.etReceiver);
        AutoCompleteTextView etCategory = bottomSheetView.findViewById(R.id.etCategory);
        AutoCompleteTextView etRecurringPayment = bottomSheetView.findViewById(R.id.etRecurringPayment);
        TextInputEditText etAmount = bottomSheetView.findViewById(R.id.etAmount);
        SwitchMaterial switchExcludeFromSummary = bottomSheetView.findViewById(R.id.switchExcludeFromSummary);
        MaterialButton btnApplyFilters = bottomSheetView.findViewById(R.id.btnApplyFilters);
        MaterialButton btnClearFilters = bottomSheetView.findViewById(R.id.btnClearFilters);
        ImageButton btnClose = bottomSheetView.findViewById(R.id.btnClose);

        // Set up date pickers
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        
        etFromDate.setOnClickListener(v -> {
            MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select From Date")
                .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
                .build();
            
            datePicker.addOnPositiveButtonClickListener(selection -> {
                Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                calendar.setTimeInMillis(selection);
                etFromDate.setText(dateFormat.format(calendar.getTime()));
            });
            
            datePicker.show(getChildFragmentManager(), "FROM_DATE_PICKER");
        });

        etToDate.setOnClickListener(v -> {
            MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select To Date")
                .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
                .build();
            
            datePicker.addOnPositiveButtonClickListener(selection -> {
                Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                calendar.setTimeInMillis(selection);
                etToDate.setText(dateFormat.format(calendar.getTime()));
            });
            
            datePicker.show(getChildFragmentManager(), "TO_DATE_PICKER");
        });

        // Set up transaction type dropdown
        String[] transactionTypes = {"DEBIT", "CREDIT"};
        ArrayAdapter<String> transactionTypeAdapter = new ArrayAdapter<>(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            transactionTypes
        ) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView text = (TextView) view.findViewById(android.R.id.text1);
                text.setTextColor(requireContext().getResources().getColor(R.color.black));
                return view;
            }
        };
        etTransactionType.setAdapter(transactionTypeAdapter);
        etTransactionType.setOnClickListener(v -> etTransactionType.showDropDown());
        etTransactionType.setDropDownBackgroundResource(android.R.color.white);

        // Set up category dropdown
        categoryViewModel.getCategoriesByType("EXPENSE").observe(getViewLifecycleOwner(), categories -> {
            List<String> categoryNames = new ArrayList<>();
            for (Category category : categories) {
                categoryNames.add(category.getName());
            }
            ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(
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
            etCategory.setAdapter(categoryAdapter);
            etCategory.setOnClickListener(v -> etCategory.showDropDown());
            etCategory.setDropDownBackgroundResource(android.R.color.white);
        });

        // Set up recurring payment dropdown
        RecurringPaymentsViewModel recurringPaymentsViewModel = new ViewModelProvider(this).get(RecurringPaymentsViewModel.class);
        recurringPaymentsViewModel.getRecurringPayments().observe(getViewLifecycleOwner(), payments -> {
            List<String> recurringPaymentNames = new ArrayList<>();
            recurringPaymentNames.add("None"); // Add "None" as first option
            for (RecurringPayment payment : payments) {
                recurringPaymentNames.add(payment.getName());
            }
            ArrayAdapter<String> recurringPaymentAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                recurringPaymentNames
            ) {
                @Override
                public View getView(int position, View convertView, ViewGroup parent) {
                    View view = super.getView(position, convertView, parent);
                    TextView text = (TextView) view.findViewById(android.R.id.text1);
                    text.setTextColor(requireContext().getResources().getColor(R.color.black));
                    return view;
                }
            };
            etRecurringPayment.setAdapter(recurringPaymentAdapter);
            etRecurringPayment.setOnClickListener(v -> etRecurringPayment.showDropDown());
            etRecurringPayment.setDropDownBackgroundResource(android.R.color.white);
        });

        // Close button
        btnClose.setOnClickListener(v -> bottomSheetDialog.dismiss());

        // Apply filters button
        btnApplyFilters.setOnClickListener(v -> {
            String description = etDescription.getText() != null ? 
                etDescription.getText().toString().trim() : "";
            String receiver = etReceiver.getText() != null ? 
                etReceiver.getText().toString().trim() : "";
            String category = etCategory.getText() != null ? 
                etCategory.getText().toString().trim() : "";
            String amountStr = etAmount.getText() != null ? 
                etAmount.getText().toString().trim() : "";
            String transactionType = etTransactionType.getText() != null ? 
                etTransactionType.getText().toString().trim() : "";
            String recurringPayment = etRecurringPayment.getText() != null ? 
                etRecurringPayment.getText().toString().trim() : "";
            String fromDate = etFromDate.getText() != null ? 
                etFromDate.getText().toString().trim() : "";
            String toDate = etToDate.getText() != null ? 
                etToDate.getText().toString().trim() : "";
            
            Double amount = null;
            if (!amountStr.isEmpty()) {
                try {
                    amount = Double.parseDouble(amountStr);
                } catch (NumberFormatException e) {
                    etAmount.setError("Invalid amount");
                    return;
                }
            }

            // Find the recurring payment ID if one is selected
            Long linkedRecurringPaymentId = null;
            if (!recurringPayment.isEmpty() && !recurringPayment.equals("None")) {
                for (RecurringPayment payment : recurringPaymentsViewModel.getRecurringPayments().getValue()) {
                    if (payment.getName().equals(recurringPayment)) {
                        linkedRecurringPaymentId = payment.getId();
                        break;
                    }
                }
            }

            viewModel.setFilters(
                description.isEmpty() ? null : description,
                receiver.isEmpty() ? null : receiver,
                category.isEmpty() ? null : category,
                amount,
                transactionType.isEmpty() ? null : transactionType,
                switchExcludeFromSummary.isChecked(),
                linkedRecurringPaymentId,
                fromDate.isEmpty() ? null : fromDate,
                toDate.isEmpty() ? null : toDate
            );
            
            bottomSheetDialog.dismiss();
        });

        // Clear filters button
        btnClearFilters.setOnClickListener(v -> {
            etDescription.setText("");
            etReceiver.setText("");
            etCategory.setText("");
            etAmount.setText("");
            etTransactionType.setText("");
            etRecurringPayment.setText("");
            etFromDate.setText("");
            etToDate.setText("");
            switchExcludeFromSummary.setChecked(false);
            viewModel.clearFilters();
            bottomSheetDialog.dismiss();
        });

        bottomSheetDialog.show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
} 