package com.example.expensemate.ui.summary;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.expensemate.R;
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
    private CategorySumAdapter adapter;
    private Calendar currentPeriod;
    private List<Account> accounts = new ArrayList<>();

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
        adapter = new CategorySumAdapter();
        binding.rvCategorySums.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvCategorySums.setAdapter(adapter);

        // Set up account dropdown
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
            Account selectedAccount = accounts.get(position);
            Log.d(TAG, "Account selected: " + selectedAccount.getName());
            viewModel.setSelectedAccount(selectedAccount.getId());
        });

        // Observe selected account changes
        viewModel.getSelectedAccountId().observe(getViewLifecycleOwner(), accountId -> {
            Log.d(TAG, "Selected account changed to: " + accountId);
            String month = viewModel.getSelectedMonth().getValue();
            String year = viewModel.getSelectedYear().getValue();
            if (month != null && year != null) {
                viewModel.getCategorySumsByMonthYearAndAccount(month, year, accountId)
                    .observe(getViewLifecycleOwner(), categorySums -> {
                        Log.d(TAG, "Category sums updated. Count: " + (categorySums != null ? categorySums.size() : 0));
                        adapter.submitList(categorySums);
                    });
            }
        });

        // Observe category sums for the selected period
        viewModel.getSelectedMonth().observe(getViewLifecycleOwner(), month -> {
            String year = viewModel.getSelectedYear().getValue();
            if (year != null) {
                Log.d(TAG, "Observing category sums for period: " + month + "/" + year);
                viewModel.getCategorySumsByMonthYearAndAccount(month, year, viewModel.getSelectedAccountId().getValue()).observe(getViewLifecycleOwner(), categorySums -> {
                    adapter.submitList(categorySums);
                });
            }
        });

        // Observe total expense
        viewModel.getTotalExpense().observe(getViewLifecycleOwner(), total -> {
            Log.d(TAG, "Total expense changed: " + total);
            binding.tvTotalAmount.setText(String.format("₹%.2f", total != null ? total : 0.0));
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
} 