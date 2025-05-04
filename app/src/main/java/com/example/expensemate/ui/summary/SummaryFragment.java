package com.example.expensemate.ui.summary;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.expensemate.databinding.FragmentSummaryBinding;
import com.example.expensemate.viewmodel.TransactionViewModel;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class SummaryFragment extends Fragment {
    private static final String TAG = "SummaryFragment";
    private FragmentSummaryBinding binding;
    private TransactionViewModel viewModel;
    private CategorySumAdapter adapter;
    private Calendar currentPeriod;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentSummaryBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        // Initialize ViewModel
        viewModel = new ViewModelProvider(requireActivity()).get(TransactionViewModel.class);

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

        // Observe category sums for the selected period
        viewModel.getSelectedMonth().observe(getViewLifecycleOwner(), month -> {
            String year = viewModel.getSelectedYear().getValue();
            if (year != null) {
                Log.d(TAG, "Observing category sums for period: " + month + "/" + year);
                viewModel.getCategorySumsByMonthYear(month, year).observe(getViewLifecycleOwner(), categorySums -> {
                    adapter.submitList(categorySums);
                });
            }
        });

        // Observe total expense
        viewModel.getTotalExpense().observe(getViewLifecycleOwner(), total -> {
            binding.tvTotalAmount.setText(String.format("₹%.2f", total));
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