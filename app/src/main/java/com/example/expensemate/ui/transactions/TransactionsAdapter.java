package com.example.expensemate.ui.transactions;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.example.expensemate.data.Transaction;
import com.example.expensemate.databinding.ItemTransactionBinding;
import java.text.SimpleDateFormat;
import java.util.Locale;

public class TransactionsAdapter extends ListAdapter<Transaction, TransactionsAdapter.TransactionViewHolder> {

    public TransactionsAdapter() {
        super(new TransactionDiffCallback());
    }

    @NonNull
    @Override
    public TransactionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemTransactionBinding binding = ItemTransactionBinding.inflate(
                LayoutInflater.from(parent.getContext()),
                parent,
                false
        );
        return new TransactionViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull TransactionViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    static class TransactionViewHolder extends RecyclerView.ViewHolder {
        private final ItemTransactionBinding binding;
        private final SimpleDateFormat dateFormat;

        public TransactionViewHolder(ItemTransactionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            this.dateFormat = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault());
        }

        public void bind(Transaction transaction) {
            binding.tvAmount.setText(String.format("₹%.2f", transaction.getAmount()));
            binding.tvDescription.setText(transaction.getDescription());
            binding.tvDate.setText(dateFormat.format(transaction.getDate()));
            binding.tvReceiver.setText(transaction.getReceiverName());
            binding.tvAccountType.setText(transaction.getAccountType());
            binding.tvAccountNumber.setText(transaction.getAccountNumber());
        }
    }

    private static class TransactionDiffCallback extends DiffUtil.ItemCallback<Transaction> {
        @Override
        public boolean areItemsTheSame(@NonNull Transaction oldItem, @NonNull Transaction newItem) {
            return oldItem.getId() == newItem.getId();
        }

        @Override
        public boolean areContentsTheSame(@NonNull Transaction oldItem, @NonNull Transaction newItem) {
            return oldItem.getAmount() == newItem.getAmount() &&
                   oldItem.getDescription().equals(newItem.getDescription()) &&
                   oldItem.getDate().equals(newItem.getDate()) &&
                   oldItem.getAccountNumber().equals(newItem.getAccountNumber()) &&
                   oldItem.getAccountType().equals(newItem.getAccountType()) &&
                   oldItem.getTransactionType().equals(newItem.getTransactionType()) &&
                   oldItem.getReceiverName().equals(newItem.getReceiverName());
        }
    }
} 