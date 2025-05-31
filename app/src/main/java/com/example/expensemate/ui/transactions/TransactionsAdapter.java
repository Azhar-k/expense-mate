package com.example.expensemate.ui.transactions;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.example.expensemate.R;
import com.example.expensemate.data.Account;
import com.example.expensemate.data.Category;
import com.example.expensemate.data.RecurringPayment;
import com.example.expensemate.data.Transaction;
import com.example.expensemate.databinding.DialogEditTransactionBinding;
import com.example.expensemate.databinding.ItemTransactionBinding;
import com.example.expensemate.ui.common.BaseDialogHelper;
import com.example.expensemate.viewmodel.AccountViewModel;
import com.example.expensemate.viewmodel.CategoryViewModel;
import com.example.expensemate.viewmodel.RecurringPaymentsViewModel;
import com.example.expensemate.viewmodel.TransactionViewModel;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TransactionsAdapter extends ListAdapter<Transaction, TransactionsAdapter.TransactionViewHolder> {
    private final TransactionViewModel viewModel;
    private final Context context;
    private final AccountViewModel accountViewModel;
    private final CategoryViewModel categoryViewModel;
    private List<RecurringPayment> currentPayments;

    public TransactionsAdapter(TransactionViewModel viewModel, Context context) {
        super(new TransactionDiffCallback());
        this.viewModel = viewModel;
        this.context = context;
        this.accountViewModel = new ViewModelProvider((FragmentActivity) context).get(AccountViewModel.class);
        this.categoryViewModel = new ViewModelProvider((FragmentActivity) context).get(CategoryViewModel.class);
    }

    public void showAddTransactionDialog() {
        DialogEditTransactionBinding dialogBinding = DialogEditTransactionBinding.inflate(
                LayoutInflater.from(context)
        );

        // Set up date and time field
        SimpleDateFormat dateTimeFormat = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault());
        Calendar calendar = Calendar.getInstance();
        dialogBinding.etDate.setText(dateTimeFormat.format(calendar.getTime()));
        
        // Set up date and time pickers
        dialogBinding.etDate.setOnClickListener(v -> {
            // Show date picker first
            DatePickerDialog datePickerDialog = new DatePickerDialog(
                context,
                (view, year, month, dayOfMonth) -> {
                    calendar.set(year, month, dayOfMonth);
                    // After date is selected, show time picker
                    TimePickerDialog timePickerDialog = new TimePickerDialog(
                        context,
                        (timeView, hourOfDay, minute) -> {
                            calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
                            calendar.set(Calendar.MINUTE, minute);
                            dialogBinding.etDate.setText(dateTimeFormat.format(calendar.getTime()));
                        },
                        calendar.get(Calendar.HOUR_OF_DAY),
                        calendar.get(Calendar.MINUTE),
                        true
                    );
                    timePickerDialog.show();
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            );
            datePickerDialog.show();
        });

        // Set up transaction types dropdown
        String[] transactionTypes = {"DEBIT", "CREDIT"};
        ArrayAdapter<String> transactionTypeAdapter = new ArrayAdapter<>(
                context,
                android.R.layout.simple_dropdown_item_1line,
                transactionTypes
        ) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView text = (TextView) view.findViewById(android.R.id.text1);
                text.setTextColor(context.getResources().getColor(R.color.black));
                return view;
            }
        };
        dialogBinding.etTransactionType.setAdapter(transactionTypeAdapter);
        dialogBinding.etTransactionType.setText("DEBIT", false); // Default to DEBIT
        dialogBinding.etTransactionType.setOnClickListener(v -> dialogBinding.etTransactionType.showDropDown());
        dialogBinding.etTransactionType.setDropDownBackgroundResource(android.R.color.white);

        // Set up accounts dropdown
        accountViewModel.getAllAccounts().observe((FragmentActivity) context, accounts -> {
            List<String> accountNames = new ArrayList<>();
            for (Account account : accounts) {
                accountNames.add(account.getName());
            }
            ArrayAdapter<String> accountAdapter = new ArrayAdapter<>(
                    context,
                    android.R.layout.simple_dropdown_item_1line,
                    accountNames
            ) {
                @Override
                public View getView(int position, View convertView, ViewGroup parent) {
                    View view = super.getView(position, convertView, parent);
                    TextView text = (TextView) view.findViewById(android.R.id.text1);
                    text.setTextColor(context.getResources().getColor(R.color.black));
                    return view;
                }
            };
            dialogBinding.etAccount.setAdapter(accountAdapter);
            
            // Set default account if available
            accountViewModel.getDefaultAccount().observe((FragmentActivity) context, defaultAccount -> {
                if (defaultAccount != null) {
                    dialogBinding.etAccount.setText(defaultAccount.getName(), false);
                } else if (!accountNames.isEmpty()) {
                    dialogBinding.etAccount.setText(accountNames.get(0), false);
                }
            });
            
            dialogBinding.etAccount.setOnClickListener(v -> dialogBinding.etAccount.showDropDown());
            dialogBinding.etAccount.setDropDownBackgroundResource(android.R.color.white);
        });

        // Set up categories dropdown
        categoryViewModel.getCategoriesByType("EXPENSE").observe((FragmentActivity) context, categories -> {
            List<String> categoryNames = new ArrayList<>();
            for (Category category : categories) {
                categoryNames.add(category.getName());
            }
            ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(
                    context,
                    android.R.layout.simple_dropdown_item_1line,
                    categoryNames
            ) {
                @Override
                public View getView(int position, View convertView, ViewGroup parent) {
                    View view = super.getView(position, convertView, parent);
                    TextView text = (TextView) view.findViewById(android.R.id.text1);
                    text.setTextColor(context.getResources().getColor(R.color.black));
                    return view;
                }
            };
            dialogBinding.etCategory.setAdapter(categoryAdapter);
            if (!categoryNames.isEmpty()) {
                dialogBinding.etCategory.setText(categoryNames.get(0), false);
            }
            dialogBinding.etCategory.setOnClickListener(v -> dialogBinding.etCategory.showDropDown());
            dialogBinding.etCategory.setDropDownBackgroundResource(android.R.color.white);
        });

        // Set up recurring payments dropdown
        RecurringPaymentsViewModel recurringPaymentsViewModel = new ViewModelProvider((FragmentActivity) context)
                .get(RecurringPaymentsViewModel.class);
        recurringPaymentsViewModel.getRecurringPayments().observe((FragmentActivity) context, payments -> {
            currentPayments = payments;
            List<String> paymentNames = new ArrayList<>();
            paymentNames.add("None"); // Add option to unlink
            for (RecurringPayment payment : payments) {
                paymentNames.add(payment.getName());
            }
            
            ArrayAdapter<String> paymentAdapter = new ArrayAdapter<>(
                    context,
                    android.R.layout.simple_dropdown_item_1line,
                    paymentNames
            ) {
                @Override
                public View getView(int position, View convertView, ViewGroup parent) {
                    View view = super.getView(position, convertView, parent);
                    TextView text = (TextView) view.findViewById(android.R.id.text1);
                    text.setTextColor(context.getResources().getColor(R.color.black));
                    return view;
                }
            };
            
            dialogBinding.etRecurringPayment.setAdapter(paymentAdapter);
            dialogBinding.etRecurringPayment.setText("None", false);
            dialogBinding.etRecurringPayment.setOnClickListener(v -> dialogBinding.etRecurringPayment.showDropDown());
            dialogBinding.etRecurringPayment.setDropDownBackgroundResource(android.R.color.white);
            dialogBinding.etRecurringPayment.setThreshold(1);
        });

        BaseDialogHelper dialogHelper = new BaseDialogHelper(
                context,
                "Add Transaction",
                dialogBinding.getRoot(),
                "Add",
                "Cancel",
                new BaseDialogHelper.OnDialogButtonClickListener() {
                    @Override
                    public void onPositiveButtonClick(AlertDialog dialog) {
                        try {
                            String amountStr = dialogBinding.etAmount.getText().toString();
                            String description = dialogBinding.etDescription.getText().toString();
                            String receiverName = dialogBinding.etReceiverName.getText().toString();
                            String category = dialogBinding.etCategory.getText().toString();
                            String transactionType = dialogBinding.etTransactionType.getText().toString();
                            String selectedAccount = dialogBinding.etAccount.getText().toString();
                            String selectedPayment = dialogBinding.etRecurringPayment.getText().toString();

                            if (amountStr.isEmpty()) {
                                Toast.makeText(context, "Please enter amount", Toast.LENGTH_SHORT).show();
                                return;
                            }

                            Transaction newTransaction = new Transaction(
                                    Double.parseDouble(amountStr),
                                    description,
                                    calendar.getTime(),
                                    transactionType,
                                    receiverName,
                                    "", // Empty SMS body for manual transactions
                                    ""  // Empty SMS sender for manual transactions
                            );
                            newTransaction.setCategory(category);

                            // Link recurring payment if selected
                            if (!selectedPayment.equals("None") && currentPayments != null) {
                                for (RecurringPayment payment : currentPayments) {
                                    if (payment.getName().equals(selectedPayment)) {
                                        newTransaction.setLinkedRecurringPaymentId(payment.getId());
                                        break;
                                    }
                                }
                            }

                            // Get selected account and link it to the transaction
                            accountViewModel.getAllAccounts().observe((FragmentActivity) context, accounts -> {
                                for (Account account : accounts) {
                                    if (account.getName().equals(selectedAccount)) {
                                        newTransaction.setAccountId(account.getId());
                                        break;
                                    }
                                }
                                viewModel.insertTransaction(newTransaction);
                                Toast.makeText(context, "Transaction added", Toast.LENGTH_SHORT).show();
                                dialog.dismiss();
                            });
                        } catch (NumberFormatException e) {
                            Toast.makeText(context, "Invalid amount", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onNegativeButtonClick(AlertDialog dialog) {
                        dialog.dismiss();
                    }
                }
        );

        AlertDialog dialog = dialogHelper.create();
        dialog.getWindow().setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        dialog.show();
    }

    @NonNull
    @Override
    public TransactionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemTransactionBinding binding = ItemTransactionBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new TransactionViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull TransactionViewHolder holder, int position) {
        Transaction transaction = getItem(position);
        holder.bind(transaction);
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
            binding.tvAmount.setText(String.format(Locale.getDefault(), "₹%.2f", transaction.getAmount()));
            binding.tvDescription.setText(transaction.getDescription());
            binding.tvDate.setText(new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
                    .format(transaction.getDate()));
            binding.tvReceiver.setText(transaction.getReceiverName());
            binding.tvCategory.setText(transaction.getCategory());
            binding.tvTransactionType.setText(transaction.getTransactionType());

            // Set up recurring payment display
            RecurringPaymentsViewModel recurringPaymentsViewModel = new ViewModelProvider((FragmentActivity) context)
                    .get(RecurringPaymentsViewModel.class);
            recurringPaymentsViewModel.getRecurringPayments().observe((FragmentActivity) context, payments -> {
                if (transaction.getLinkedRecurringPaymentId() != null) {
                    for (RecurringPayment payment : payments) {
                        if (payment.getId() == transaction.getLinkedRecurringPaymentId()) {
                            binding.tvLinkedPayment.setVisibility(View.VISIBLE);
                            binding.tvLinkedPayment.setText("Linked to: " + payment.getName());
                            break;
                        }
                    }
                } else {
                    binding.tvLinkedPayment.setVisibility(View.GONE);
                }
            });

            // Set up exclude from summary checkbox
            binding.cbExcludeFromSummary.setOnCheckedChangeListener(null); // Remove any existing listener
            binding.cbExcludeFromSummary.setChecked(transaction.isExcludedFromSummary());
            binding.cbExcludeFromSummary.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked != transaction.isExcludedFromSummary()) { // Only update if state actually changed
                    transaction.setExcludedFromSummary(isChecked);
                    viewModel.updateTransaction(transaction, transaction);
                    Toast.makeText(context, 
                        isChecked ? "Transaction excluded from summary" : "Transaction included in summary", 
                        Toast.LENGTH_SHORT).show();
                }
            });
        }

        private void showEditDialog(Transaction transaction) {
            DialogEditTransactionBinding dialogBinding = DialogEditTransactionBinding.inflate(
                    LayoutInflater.from(context)
            );

            // Pre-fill the fields
            dialogBinding.etAmount.setText(String.valueOf(transaction.getAmount()));
            dialogBinding.etDescription.setText(transaction.getDescription());
            dialogBinding.etReceiverName.setText(transaction.getReceiverName());
            dialogBinding.etCategory.setText(transaction.getCategory());
            dialogBinding.etTransactionType.setText(transaction.getTransactionType());
            
            // Set up date and time field
            SimpleDateFormat dateTimeFormat = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault());
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(transaction.getDate());
            dialogBinding.etDate.setText(dateTimeFormat.format(calendar.getTime()));
            
            // Set up date and time pickers
            dialogBinding.etDate.setOnClickListener(v -> {
                // Show date picker first
                DatePickerDialog datePickerDialog = new DatePickerDialog(
                    context,
                    (view, year, month, dayOfMonth) -> {
                        calendar.set(year, month, dayOfMonth);
                        // After date is selected, show time picker
                        TimePickerDialog timePickerDialog = new TimePickerDialog(
                            context,
                            (timeView, hourOfDay, minute) -> {
                                calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
                                calendar.set(Calendar.MINUTE, minute);
                                dialogBinding.etDate.setText(dateTimeFormat.format(calendar.getTime()));
                            },
                            calendar.get(Calendar.HOUR_OF_DAY),
                            calendar.get(Calendar.MINUTE),
                            true
                        );
                        timePickerDialog.show();
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)
                );
                datePickerDialog.show();
            });

            // Set up transaction type dropdown
            String[] transactionTypes = {"DEBIT", "CREDIT"};
            ArrayAdapter<String> transactionTypeAdapter = new ArrayAdapter<>(
                    context,
                    android.R.layout.simple_dropdown_item_1line,
                    transactionTypes
            ) {
                @Override
                public View getView(int position, View convertView, ViewGroup parent) {
                    View view = super.getView(position, convertView, parent);
                    TextView text = (TextView) view.findViewById(android.R.id.text1);
                    text.setTextColor(context.getResources().getColor(R.color.black));
                    return view;
                }
            };
            dialogBinding.etTransactionType.setAdapter(transactionTypeAdapter);
            dialogBinding.etTransactionType.setText(transaction.getTransactionType(), false);
            dialogBinding.etTransactionType.setOnClickListener(v -> dialogBinding.etTransactionType.showDropDown());
            dialogBinding.etTransactionType.setDropDownBackgroundResource(android.R.color.white);

            // Set up accounts dropdown
            accountViewModel.getAllAccounts().observe((FragmentActivity) context, accounts -> {
                List<String> accountNames = new ArrayList<>();
                for (Account account : accounts) {
                    accountNames.add(account.getName());
                }
                ArrayAdapter<String> accountAdapter = new ArrayAdapter<>(
                        context,
                        android.R.layout.simple_dropdown_item_1line,
                        accountNames
                ) {
                    @Override
                    public View getView(int position, View convertView, ViewGroup parent) {
                        View view = super.getView(position, convertView, parent);
                        TextView text = (TextView) view.findViewById(android.R.id.text1);
                        text.setTextColor(context.getResources().getColor(R.color.black));
                        return view;
                    }
                };
                dialogBinding.etAccount.setAdapter(accountAdapter);
                
                // Set current account if any
                if (transaction.getAccountId() != null) {
                    for (Account account : accounts) {
                        if (account.getId() == transaction.getAccountId()) {
                            dialogBinding.etAccount.setText(account.getName(), false);
                            break;
                        }
                    }
                } else {
                    // Set default account if no account is linked
                    accountViewModel.getDefaultAccount().observe((FragmentActivity) context, defaultAccount -> {
                        if (defaultAccount != null) {
                            dialogBinding.etAccount.setText(defaultAccount.getName(), false);
                        } else if (!accountNames.isEmpty()) {
                            dialogBinding.etAccount.setText(accountNames.get(0), false);
                        }
                    });
                }
                
                dialogBinding.etAccount.setOnClickListener(v -> dialogBinding.etAccount.showDropDown());
                dialogBinding.etAccount.setDropDownBackgroundResource(android.R.color.white);
            });

            // Set up categories dropdown
            categoryViewModel.getCategoriesByType("EXPENSE").observe((FragmentActivity) context, categories -> {
                List<String> categoryNames = new ArrayList<>();
                for (Category category : categories) {
                    categoryNames.add(category.getName());
                }
                ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(
                        context,
                        android.R.layout.simple_dropdown_item_1line,
                        categoryNames
                ) {
                    @Override
                    public View getView(int position, View convertView, ViewGroup parent) {
                        View view = super.getView(position, convertView, parent);
                        TextView text = (TextView) view.findViewById(android.R.id.text1);
                        text.setTextColor(context.getResources().getColor(R.color.black));
                        return view;
                    }
                };
                dialogBinding.etCategory.setAdapter(categoryAdapter);
                dialogBinding.etCategory.setText(transaction.getCategory(), false);
                dialogBinding.etCategory.setOnClickListener(v -> dialogBinding.etCategory.showDropDown());
                dialogBinding.etCategory.setDropDownBackgroundResource(android.R.color.white);
            });

            // Set up recurring payments dropdown
            RecurringPaymentsViewModel recurringPaymentsViewModel = new ViewModelProvider((FragmentActivity) context)
                    .get(RecurringPaymentsViewModel.class);
            recurringPaymentsViewModel.getRecurringPayments().observe((FragmentActivity) context, payments -> {
                currentPayments = payments;
                List<String> paymentNames = new ArrayList<>();
                paymentNames.add("None"); // Add option to unlink
                for (RecurringPayment payment : payments) {
                    paymentNames.add(payment.getName());
                }
                
                ArrayAdapter<String> paymentAdapter = new ArrayAdapter<>(
                        context,
                        android.R.layout.simple_dropdown_item_1line,
                        paymentNames
                ) {
                    @Override
                    public View getView(int position, View convertView, ViewGroup parent) {
                        View view = super.getView(position, convertView, parent);
                        TextView text = (TextView) view.findViewById(android.R.id.text1);
                        text.setTextColor(context.getResources().getColor(R.color.black));
                        return view;
                    }
                };
                
                dialogBinding.etRecurringPayment.setAdapter(paymentAdapter);
                
                // Set current linked payment if any
                if (transaction.getLinkedRecurringPaymentId() != null) {
                    for (RecurringPayment payment : payments) {
                        if (payment.getId() == transaction.getLinkedRecurringPaymentId()) {
                            dialogBinding.etRecurringPayment.setText(payment.getName(), false);
                            break;
                        }
                    }
                } else {
                    dialogBinding.etRecurringPayment.setText("None", false);
                }
                
                dialogBinding.etRecurringPayment.setOnClickListener(v -> dialogBinding.etRecurringPayment.showDropDown());
                dialogBinding.etRecurringPayment.setDropDownBackgroundResource(android.R.color.white);
                dialogBinding.etRecurringPayment.setThreshold(1);
            });

            BaseDialogHelper dialogHelper = new BaseDialogHelper(
                    context,
                    "Edit Transaction",
                    dialogBinding.getRoot(),
                    "Save",
                    "Cancel",
                    new BaseDialogHelper.OnDialogButtonClickListener() {
                        @Override
                        public void onPositiveButtonClick(AlertDialog dialog) {
                            try {
                                String amountStr = dialogBinding.etAmount.getText().toString();
                                String description = dialogBinding.etDescription.getText().toString();
                                String receiverName = dialogBinding.etReceiverName.getText().toString();
                                String category = dialogBinding.etCategory.getText().toString();
                                String transactionType = dialogBinding.etTransactionType.getText().toString();
                                String selectedPayment = dialogBinding.etRecurringPayment.getText().toString();
                                String selectedAccount = dialogBinding.etAccount.getText().toString();

                                if (amountStr.isEmpty()) {
                                    Toast.makeText(context, "Please enter amount", Toast.LENGTH_SHORT).show();
                                    return;
                                }

                                Transaction updatedTransaction = new Transaction(
                                        Double.parseDouble(amountStr),
                                        description,
                                        calendar.getTime(),
                                        transactionType,
                                        receiverName,
                                        transaction.getSmsBody(),
                                        transaction.getSmsSender()
                                );
                                updatedTransaction.setId(transaction.getId());
                                updatedTransaction.setCategory(category);

                                // Get selected account and link it to the transaction
                                accountViewModel.getAllAccounts().observe((FragmentActivity) context, accounts -> {
                                    for (Account account : accounts) {
                                        if (account.getName().equals(selectedAccount)) {
                                            updatedTransaction.setAccountId(account.getId());
                                            break;
                                        }
                                    }
                                });

                                // Handle recurring payment linking
                                if (!selectedPayment.equals("None")) {
                                    for (RecurringPayment payment : currentPayments) {
                                        if (payment.getName().equals(selectedPayment)) {
                                            // Link the payment to the transaction
                                            updatedTransaction.setLinkedRecurringPaymentId(payment.getId());
                                            // Mark the recurring payment as completed
                                            payment.setCompleted(true);
                                            payment.setLastCompletedDate(calendar.getTime());
                                            recurringPaymentsViewModel.update(payment);

                                            // Update transaction and notify adapter
                                            viewModel.updateTransaction(transaction, updatedTransaction);
                                            Toast.makeText(context, "Transaction updated", Toast.LENGTH_SHORT).show();
                                            dialog.dismiss();
                                            return;
                                        }
                                    }
                                } else {
                                    // No recurring payment selected, update transaction
                                    updatedTransaction.setLinkedRecurringPaymentId(null);
                                    viewModel.updateTransaction(transaction, updatedTransaction);
                                    Toast.makeText(context, "Transaction updated", Toast.LENGTH_SHORT).show();
                                    dialog.dismiss();
                                }
                            } catch (NumberFormatException e) {
                                Toast.makeText(context, "Invalid amount", Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onNegativeButtonClick(AlertDialog dialog) {
                            dialog.dismiss();
                        }
                    }
            );

            dialogHelper.create().show();
        }

        private void showDeleteConfirmationDialog(Transaction transaction) {
            BaseDialogHelper dialogHelper = new BaseDialogHelper(
                    context,
                    "Delete Transaction",
                    null,
                    "Delete",
                    "Cancel",
                    new BaseDialogHelper.OnDialogButtonClickListener() {
                        @Override
                        public void onPositiveButtonClick(AlertDialog dialog) {
                            viewModel.deleteTransaction(transaction);
                            Toast.makeText(context, "Transaction deleted", Toast.LENGTH_SHORT).show();
                            dialog.dismiss();
                        }

                        @Override
                        public void onNegativeButtonClick(AlertDialog dialog) {
                            dialog.dismiss();
                        }
                    }
            );

            dialogHelper.setMessage("Are you sure you want to delete this transaction?");
            dialogHelper.create().show();
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