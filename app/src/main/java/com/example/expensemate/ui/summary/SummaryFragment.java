package com.example.expensemate.ui.summary;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.expensemate.databinding.FragmentSummaryBinding;
import com.example.expensemate.viewmodel.TransactionViewModel;

public class SummaryFragment extends Fragment {
    private FragmentSummaryBinding binding;
    private TransactionViewModel viewModel;
    private CategorySumAdapter adapter;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentSummaryBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        // Initialize ViewModel
        viewModel = new ViewModelProvider(requireActivity()).get(TransactionViewModel.class);

        // Setup RecyclerView
        adapter = new CategorySumAdapter();
        binding.rvCategorySums.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvCategorySums.setAdapter(adapter);

        // Observe category sums
        viewModel.getCategorySums().observe(getViewLifecycleOwner(), categorySums -> {
            adapter.submitList(categorySums);
        });

        // Observe total expense
        viewModel.getTotalExpense().observe(getViewLifecycleOwner(), total -> {
            binding.tvTotalAmount.setText(String.format("₹%.2f", total));
        });

        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
} 