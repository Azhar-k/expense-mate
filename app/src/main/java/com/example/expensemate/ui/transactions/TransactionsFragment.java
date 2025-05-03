package com.example.expensemate.ui.transactions;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.expensemate.databinding.FragmentTransactionsBinding;
import com.example.expensemate.viewmodel.TransactionViewModel;

public class TransactionsFragment extends Fragment {
    private FragmentTransactionsBinding binding;
    private TransactionViewModel viewModel;
    private TransactionsAdapter adapter;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentTransactionsBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        // Initialize ViewModel
        viewModel = new ViewModelProvider(requireActivity()).get(TransactionViewModel.class);

        // Setup RecyclerView
        adapter = new TransactionsAdapter(viewModel, requireContext());
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.recyclerView.setAdapter(adapter);

        // Set up FAB
        binding.fabAddTransaction.setOnClickListener(v -> adapter.showAddTransactionDialog());

        // Observe transactions
        viewModel.getAllTransactions().observe(getViewLifecycleOwner(), transactions -> {
            adapter.submitList(transactions);
            // Show/hide empty state
            binding.tvEmptyState.setVisibility(transactions.isEmpty() ? View.VISIBLE : View.GONE);
        });

        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
} 