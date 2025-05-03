package com.example.expensemate.ui.transactions;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.example.expensemate.R;
import com.example.expensemate.data.Transaction;
import com.example.expensemate.databinding.DialogEditTransactionBinding;
import com.example.expensemate.databinding.ItemTransactionBinding;
import com.example.expensemate.viewmodel.TransactionViewModel;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class TransactionsAdapter extends ListAdapter<Transaction, TransactionsAdapter.TransactionViewHolder> {
    private final TransactionViewModel viewModel;
    private final SimpleDateFormat dateFormat;
    private final Context context;

    public TransactionsAdapter(TransactionViewModel viewModel, Context context) {
        super(new TransactionDiffCallback());
        this.viewModel = viewModel;
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

    public void showAddTransactionDialog() {
        DialogEditTransactionBinding dialogBinding = DialogEditTransactionBinding.inflate(
                LayoutInflater.from(context)
        );

        // Set up transaction type dropdown
        String[] transactionTypes = {"DEBIT", "CREDIT"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                context,
                android.R.layout.simple_dropdown_item_1line,
                transactionTypes
        );
        dialogBinding.etTransactionType.setAdapter(adapter);
        dialogBinding.etTransactionType.setText("DEBIT", false); // Default to DEBIT

        AlertDialog dialog = new AlertDialog.Builder(context, 
                com.google.android.material.R.style.Theme_MaterialComponents_Light_Dialog)
                .setView(dialogBinding.getRoot())
                .setPositiveButton("Add", null)
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(dialogInterface -> {
            Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            Button negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            
            positiveButton.setTextColor(context.getResources().getColor(R.color.primary));
            negativeButton.setTextColor(context.getResources().getColor(R.color.primary));
            
            positiveButton.setOnClickListener(v -> {
                try {
                    String amountStr = dialogBinding.etAmount.getText().toString();
                    String description = dialogBinding.etDescription.getText().toString();
                    String receiverName = dialogBinding.etReceiverName.getText().toString();
                    String accountNumber = dialogBinding.etAccountNumber.getText().toString();
                    String accountType = dialogBinding.etAccountType.getText().toString();
                    String transactionType = dialogBinding.etTransactionType.getText().toString();

                    if (amountStr.isEmpty()) {
                        Toast.makeText(context, "Please enter amount", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Transaction newTransaction = new Transaction(
                            Double.parseDouble(amountStr),
                            description,
                            new Date(),
                            accountNumber,
                            accountType,
                            transactionType,
                            receiverName,
                            "", // Empty SMS body for manual transactions
                            ""  // Empty SMS sender for manual transactions
                    );
                    viewModel.insertTransaction(newTransaction);
                    Toast.makeText(context, "Transaction added", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                } catch (NumberFormatException e) {
                    Toast.makeText(context, "Invalid amount", Toast.LENGTH_SHORT).show();
                }
            });
        });

        dialog.show();
    }

    class TransactionViewHolder extends RecyclerView.ViewHolder {
        private final ItemTransactionBinding binding;

        public TransactionViewHolder(ItemTransactionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;

            // Set up click listeners for edit and delete
            binding.btnEdit.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    showEditDialog(getItem(position));
                }
            });

            binding.btnDelete.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    showDeleteConfirmationDialog(getItem(position));
                }
            });
        }

        public void bind(Transaction transaction) {
            binding.tvAmount.setText(String.format("₹%.2f", transaction.getAmount()));
            binding.tvDescription.setText(transaction.getDescription());
            binding.tvDate.setText(dateFormat.format(transaction.getDate()));
            binding.tvReceiver.setText(transaction.getReceiverName());
            binding.tvAccountType.setText(transaction.getAccountType());
            binding.tvAccountNumber.setText(transaction.getAccountNumber());
        }

        private void showEditDialog(Transaction transaction) {
            DialogEditTransactionBinding dialogBinding = DialogEditTransactionBinding.inflate(
                    LayoutInflater.from(context)
            );

            // Pre-fill the fields
            dialogBinding.etAmount.setText(String.valueOf(transaction.getAmount()));
            dialogBinding.etDescription.setText(transaction.getDescription());
            dialogBinding.etReceiverName.setText(transaction.getReceiverName());
            dialogBinding.etAccountNumber.setText(transaction.getAccountNumber());
            dialogBinding.etAccountType.setText(transaction.getAccountType());

            // Set up transaction type dropdown
            String[] transactionTypes = {"DEBIT", "CREDIT"};
            ArrayAdapter<String> adapter = new ArrayAdapter<>(
                    context,
                    android.R.layout.simple_dropdown_item_1line,
                    transactionTypes
            );
            dialogBinding.etTransactionType.setAdapter(adapter);
            dialogBinding.etTransactionType.setText(transaction.getTransactionType(), false);

            AlertDialog dialog = new AlertDialog.Builder(context, 
                    com.google.android.material.R.style.Theme_MaterialComponents_Light_Dialog)
                    .setView(dialogBinding.getRoot())
                    .setPositiveButton("Save", null)  // Set to null initially
                    .setNegativeButton("Cancel", null)  // Set to null initially
                    .create();

            dialog.setOnShowListener(dialogInterface -> {
                Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                Button negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
                
                positiveButton.setTextColor(context.getResources().getColor(R.color.primary));
                negativeButton.setTextColor(context.getResources().getColor(R.color.primary));
                
                positiveButton.setOnClickListener(v -> {
                    try {
                        String amountStr = dialogBinding.etAmount.getText().toString();
                        String description = dialogBinding.etDescription.getText().toString();
                        String receiverName = dialogBinding.etReceiverName.getText().toString();
                        String accountNumber = dialogBinding.etAccountNumber.getText().toString();
                        String accountType = dialogBinding.etAccountType.getText().toString();
                        String transactionType = dialogBinding.etTransactionType.getText().toString();

                        if (amountStr.isEmpty()) {
                            Toast.makeText(context, "Please enter amount", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        Transaction updatedTransaction = new Transaction(
                                Double.parseDouble(amountStr),
                                description,
                                transaction.getDate(), // Keep the original date
                                accountNumber,
                                accountType,
                                transactionType,
                                receiverName,
                                transaction.getSmsBody(), // Keep the original SMS body
                                transaction.getSmsSender() // Keep the original SMS sender
                        );
                        updatedTransaction.setId(transaction.getId());
                        viewModel.updateTransaction(transaction, updatedTransaction);
                        Toast.makeText(context, "Transaction updated", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    } catch (NumberFormatException e) {
                        Toast.makeText(context, "Invalid amount", Toast.LENGTH_SHORT).show();
                    }
                });
            });

            dialog.show();
        }

        private void showDeleteConfirmationDialog(Transaction transaction) {
            new AlertDialog.Builder(context)
                    .setTitle("Delete Transaction")
                    .setMessage("Are you sure you want to delete this transaction?")
                    .setPositiveButton("Delete", (dialog, which) -> {
                        viewModel.deleteTransaction(transaction);
                        Toast.makeText(context, "Transaction deleted", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
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