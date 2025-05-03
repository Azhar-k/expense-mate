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
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize ViewModel
        viewModel = new ViewModelProvider(this).get(TransactionViewModel.class);

        // Setup RecyclerView
        adapter = new TransactionsAdapter();
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerView.setAdapter(adapter);

        // Observe transactions
        viewModel.getAllTransactions().observe(getViewLifecycleOwner(), transactions -> {
            adapter.submitList(transactions);
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
} 