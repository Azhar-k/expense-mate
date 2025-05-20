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
import com.example.expensemate.data.Account;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class TransactionsFragment extends Fragment {
    private static final String TAG = "TransactionsFragment";
    private FragmentTransactionsBinding binding;
    private TransactionViewModel viewModel;
    private AccountViewModel accountViewModel;
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
} 