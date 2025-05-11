package com.example.expensemate.ui.transactions;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.expensemate.databinding.FragmentTransactionsBinding;
import com.example.expensemate.viewmodel.TransactionViewModel;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class TransactionsFragment extends Fragment {
    private static final String TAG = "TransactionsFragment";
    private FragmentTransactionsBinding binding;
    private TransactionViewModel viewModel;
    private TransactionsAdapter adapter;
    private Calendar currentPeriod;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentTransactionsBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        // Initialize ViewModel
        viewModel = new ViewModelProvider(requireActivity()).get(TransactionViewModel.class);
        Log.d(TAG, "ViewModel initialized");

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