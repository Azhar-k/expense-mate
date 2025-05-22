package com.example.expensemate.ui.summary;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.expensemate.R;
import com.example.expensemate.data.CategorySum;
import com.example.expensemate.data.Transaction;
import com.example.expensemate.databinding.FragmentSummaryBinding;
import com.example.expensemate.data.Account;
import com.example.expensemate.viewmodel.AccountViewModel;
import com.example.expensemate.viewmodel.TransactionViewModel;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class SummaryFragment extends Fragment {
    private static final String TAG = "SummaryFragment";
    private FragmentSummaryBinding binding;
    private TransactionViewModel viewModel;
    private AccountViewModel accountViewModel;
    private CategorySumAdapter categoryAdapter;
    private Calendar currentPeriod;
    private List<Account> accounts = new ArrayList<>();
    private boolean showingExpenses = true;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentSummaryBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        // Initialize ViewModels
        viewModel = new ViewModelProvider(requireActivity()).get(TransactionViewModel.class);
        accountViewModel = new ViewModelProvider(requireActivity()).get(AccountViewModel.class);

        // Initialize current period
        currentPeriod = Calendar.getInstance();
        updatePeriodDisplay();
        updateSelectedPeriod();

        // Set up month navigation
        binding.btnPrevMonth.setOnClickListener(v -> {
            currentPeriod.add(Calendar.MONTH, -1);
            updatePeriodDisplay();
            updateSelectedPeriod();
        });

        binding.btnNextMonth.setOnClickListener(v -> {
            currentPeriod.add(Calendar.MONTH, 1);
            updatePeriodDisplay();
            updateSelectedPeriod();
        });

        // Setup RecyclerView
        categoryAdapter = new CategorySumAdapter(false);
        binding.rvCategories.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvCategories.setAdapter(categoryAdapter);

        // Set up category click listener
        categoryAdapter.setOnCategoryClickListener(categorySum -> {
            String month = viewModel.getSelectedMonth().getValue();
            String year = viewModel.getSelectedYear().getValue();
            Long accountId = viewModel.getSelectedAccountId().getValue();

            if (month != null && year != null) {
                // Get transactions for the selected category
                viewModel.getTransactionsByCategoryAndPeriod(
                    categorySum.getCategory(),
                    month,
                    year,
                    accountId,
                    showingExpenses ? "DEBIT" : "CREDIT"
                ).observe(getViewLifecycleOwner(), transactions -> {
                    if (transactions != null && !transactions.isEmpty()) {
                        showCategoryTransactionsDialog(categorySum, transactions);
                    }
                });
            }
        });

        // Set up breakdown toggle
        binding.breakdownToggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                showingExpenses = checkedId == R.id.btn_expense_breakdown;
                updateBreakdownLabel();
                updateCategoryList();
            }
        });

        // Set initial selection
        binding.breakdownToggle.check(R.id.btn_expense_breakdown);

        // Set up account dropdown
        accountViewModel.getAllAccounts().observe(getViewLifecycleOwner(), accountList -> {
            accounts = accountList;
            List<String> accountNames = new ArrayList<>();
            for (Account account : accounts) {
                accountNames.add(account.getName());
            }
            // Add "All" as the second option
            accountNames.add(1, "All");
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
            binding.accountDropdown.setAdapter(adapter);
            binding.accountDropdown.setDropDownBackgroundResource(android.R.color.white);
        });

        // Observe default account
        accountViewModel.getDefaultAccount().observe(getViewLifecycleOwner(), defaultAccount -> {
            if (defaultAccount != null) {
                binding.accountDropdown.setText(defaultAccount.getName(), false);
                viewModel.setSelectedAccount(defaultAccount.getId());
            }
        });

        // Handle account selection
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

        // Observe selected account changes
        viewModel.getSelectedAccountId().observe(getViewLifecycleOwner(), accountId -> {
            Log.d(TAG, "Selected account changed to: " + accountId);
            updateCategoryList();
        });

        // Observe category sums for the selected period
        viewModel.getSelectedMonth().observe(getViewLifecycleOwner(), month -> {
            String year = viewModel.getSelectedYear().getValue();
            if (year != null) {
                Log.d(TAG, "Observing category sums for period: " + month + "/" + year);
                updateCategoryList();
            }
        });

        // Observe total expense
        viewModel.getTotalExpense().observe(getViewLifecycleOwner(), total -> {
            Log.d(TAG, "Total expense changed: " + total);
            binding.tvTotalAmount.setText(String.format("₹%.2f", total != null ? total : 0.0));
        });

        // Observe total income
        viewModel.getTotalIncome().observe(getViewLifecycleOwner(), total -> {
            Log.d(TAG, "Total income changed: " + total);
            binding.tvTotalIncome.setText(String.format("₹%.2f", total != null ? total : 0.0));
        });

        return root;
    }

    private void updatePeriodDisplay() {
        SimpleDateFormat monthYearFormat = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
        binding.tvPeriod.setText(monthYearFormat.format(currentPeriod.getTime()));
    }

    private void updateSelectedPeriod() {
        SimpleDateFormat monthFormat = new SimpleDateFormat("MM", Locale.getDefault());
        SimpleDateFormat yearFormat = new SimpleDateFormat("yyyy", Locale.getDefault());
        String month = monthFormat.format(currentPeriod.getTime());
        String year = yearFormat.format(currentPeriod.getTime());
        Log.d(TAG, "Updating period to: " + month + "/" + year);
        viewModel.setSelectedMonthYear(month, year);
    }

    private void updateBreakdownLabel() {
        binding.breakdownLabel.setText(showingExpenses ? "Expense Categories" : "Income Categories");
    }

    private void updateCategoryList() {
        String month = viewModel.getSelectedMonth().getValue();
        String year = viewModel.getSelectedYear().getValue();
        Long accountId = viewModel.getSelectedAccountId().getValue();

        if (month != null && year != null) {
            LiveData<List<CategorySum>> categorySums = showingExpenses ?
                viewModel.getCategorySumsByMonthYearAndAccount(month, year, accountId) :
                viewModel.getIncomeCategorySumsByMonthYearAndAccount(month, year, accountId);

            categorySums.observe(getViewLifecycleOwner(), sums -> {
                categoryAdapter.setIncome(!showingExpenses);
                categoryAdapter.submitList(sums);
            });
        }
    }

    private void showCategoryTransactionsDialog(CategorySum categorySum, List<Transaction> transactions) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        
        // Create custom title view
        TextView titleView = new TextView(requireContext());
        titleView.setText(categorySum.getCategory() + " Transactions");
        titleView.setTextColor(requireContext().getColor(android.R.color.black));
        titleView.setTextSize(20);
        titleView.setPadding(50, 30, 50, 30);
        builder.setCustomTitle(titleView);

        // Create RecyclerView for transactions
        RecyclerView recyclerView = new RecyclerView(requireContext());
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setBackgroundColor(requireContext().getColor(android.R.color.white));
        CategoryTransactionsAdapter adapter = new CategoryTransactionsAdapter(requireContext());
        recyclerView.setAdapter(adapter);
        adapter.submitList(transactions);

        // Set dialog content
        builder.setView(recyclerView);
        builder.setPositiveButton("Close", null);
        
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(dialogInterface -> {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.white);
        });
        dialog.show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
} 