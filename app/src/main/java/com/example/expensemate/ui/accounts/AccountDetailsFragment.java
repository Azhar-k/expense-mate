package com.example.expensemate.ui.accounts;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.expensemate.R;
import com.example.expensemate.data.Account;
import com.example.expensemate.databinding.FragmentAccountDetailsBinding;
import com.example.expensemate.ui.transactions.TransactionsAdapter;
import com.example.expensemate.viewmodel.AccountViewModel;
import com.example.expensemate.viewmodel.TransactionViewModel;
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AccountDetailsFragment extends Fragment {
    private static final String TAG = "AccountDetailsFragment";
    private FragmentAccountDetailsBinding binding;
    private AccountViewModel accountViewModel;
    private TransactionViewModel transactionViewModel;
    private TransactionsAdapter adapter;
    private ExecutorService executorService;
    private Calendar startDate;
    private Calendar endDate;
    private SimpleDateFormat dateFormat;
    private long accountId;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        executorService = Executors.newSingleThreadExecutor();
        dateFormat = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
        
        // Initialize dates
        startDate = Calendar.getInstance();
        endDate = Calendar.getInstance();
        startDate.add(Calendar.DAY_OF_MONTH, -30); // Default to last 30 days
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentAccountDetailsBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        // Get account ID from arguments
        if (getArguments() != null) {
            accountId = getArguments().getLong("accountId");
        }

        // Initialize ViewModels
        accountViewModel = new ViewModelProvider(requireActivity()).get(AccountViewModel.class);
        transactionViewModel = new ViewModelProvider(requireActivity()).get(TransactionViewModel.class);

        // Setup RecyclerView
        adapter = new TransactionsAdapter(transactionViewModel, requireContext());
        binding.rvTransactions.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvTransactions.setAdapter(adapter);

        // Setup date buttons
        updateDateButtonTexts();
        setupDateButtons();

        // Calculate initial balance and load transactions
        calculateTotalBalance();
        loadTransactions();

        return root;
    }

    private void setupDateButtons() {
        binding.btnStartDate.setOnClickListener(v -> showDatePicker(true));
        binding.btnEndDate.setOnClickListener(v -> showDatePicker(false));
    }

    private void showDatePicker(boolean isStartDate) {
        Calendar calendar = isStartDate ? startDate : endDate;
        DatePickerDialog datePickerDialog = new DatePickerDialog(
                requireContext(),
                (view, year, month, dayOfMonth) -> {
                    calendar.set(year, month, dayOfMonth);
                    if (isStartDate) {
                        if (calendar.after(endDate)) {
                            endDate.set(year, month, dayOfMonth);
                        }
                    } else {
                        if (calendar.before(startDate)) {
                            startDate.set(year, month, dayOfMonth);
                        }
                    }
                    // Set end date to end of day when dates are same
                    if (startDate.get(Calendar.YEAR) == endDate.get(Calendar.YEAR) &&
                        startDate.get(Calendar.MONTH) == endDate.get(Calendar.MONTH) &&
                        startDate.get(Calendar.DAY_OF_MONTH) == endDate.get(Calendar.DAY_OF_MONTH)) {
                        endDate.set(Calendar.HOUR_OF_DAY, 23);
                        endDate.set(Calendar.MINUTE, 59);
                        endDate.set(Calendar.SECOND, 59);
                    }
                    updateDateButtonTexts();
                    loadTransactions();
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );
        datePickerDialog.show();
    }

    private void updateDateButtonTexts() {
        binding.btnStartDate.setText(dateFormat.format(startDate.getTime()));
        binding.btnEndDate.setText(dateFormat.format(endDate.getTime()));
    }

    private void calculateTotalBalance() {
        executorService.execute(() -> {
            double totalIncome = transactionViewModel.getTotalIncomeForAccount(accountId);
            double totalExpense = transactionViewModel.getTotalExpenseForAccount(accountId);
            double balance = totalIncome - totalExpense;
            
            requireActivity().runOnUiThread(() -> {
                TextView balanceText = binding.tvTotalBalance;
                balanceText.setText(String.format("₹%.2f", balance));
                balanceText.setTextColor(requireContext().getColor(
                    balance >= 0 ? R.color.credit_color : R.color.debit_color
                ));
            });
        });
    }

    private void loadTransactions() {
        transactionViewModel.getTransactionsByDateRangeAndAccount(
            startDate.getTime(),
            endDate.getTime(),
            accountId
        ).observe(getViewLifecycleOwner(), transactions -> {
            adapter.submitList(transactions);
            binding.tvEmptyState.setVisibility(
                transactions != null && transactions.isEmpty() ? View.VISIBLE : View.GONE
            );
            // Always scroll to top when transactions change
            if (transactions != null && !transactions.isEmpty()) {
                binding.rvTransactions.postDelayed(() -> {
                    binding.rvTransactions.scrollToPosition(0);
                    binding.rvTransactions.smoothScrollToPosition(0);
                }, 100);
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
} 