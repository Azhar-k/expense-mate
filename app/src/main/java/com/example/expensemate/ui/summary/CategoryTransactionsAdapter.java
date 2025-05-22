package com.example.expensemate.ui.summary;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.example.expensemate.R;
import com.example.expensemate.data.Transaction;
import com.example.expensemate.databinding.ItemTransactionBinding;
import java.text.SimpleDateFormat;
import java.util.Locale;

public class CategoryTransactionsAdapter extends ListAdapter<Transaction, CategoryTransactionsAdapter.TransactionViewHolder> {
    private final SimpleDateFormat dateFormat;
    private final Context context;

    public CategoryTransactionsAdapter(Context context) {
        super(new TransactionDiffCallback());
        this.context = context;
        this.dateFormat = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault());
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

    class TransactionViewHolder extends RecyclerView.ViewHolder {
        private final ItemTransactionBinding binding;

        public TransactionViewHolder(ItemTransactionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            
            // Hide edit and delete buttons
            binding.btnEdit.setVisibility(View.GONE);
            binding.btnDelete.setVisibility(View.GONE);
        }

        public void bind(Transaction transaction) {
            binding.tvAmount.setText(String.format("₹%.2f", transaction.getAmount()));
            binding.tvAmount.setTextColor(context.getColor(
                    transaction.getTransactionType().equals("DEBIT") ? R.color.debit_color : R.color.credit_color));
            binding.tvDate.setText(String.format("Date: %s", dateFormat.format(transaction.getDate())));
            binding.tvCategory.setText(String.format("Category: %s", transaction.getCategory()));
            binding.tvDescription.setText(String.format("Description: %s", transaction.getDescription()));
            binding.tvTransactionType.setText(String.format("Type: %s", transaction.getTransactionType()));
            binding.tvReceiver.setText(String.format("Receiver: %s", transaction.getReceiverName()));
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
                   oldItem.getReceiverName().equals(newItem.getReceiverName()) &&
                   oldItem.getCategory().equals(newItem.getCategory()) &&
                   oldItem.getTransactionType().equals(newItem.getTransactionType());
        }
    }
} 