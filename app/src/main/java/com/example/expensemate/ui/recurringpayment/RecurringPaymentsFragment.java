package com.example.expensemate.ui.recurringpayment;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.Toast;
import android.widget.TextView;
import android.widget.ImageButton;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.expensemate.R;
import com.example.expensemate.data.Account;
import com.example.expensemate.data.RecurringPayment;
import com.example.expensemate.data.Transaction;
import com.example.expensemate.databinding.DialogEditRecurringPaymentBinding;
import com.example.expensemate.ui.DatePickerHelper;
import com.example.expensemate.ui.common.BaseDialogHelper;
import com.example.expensemate.viewmodel.AccountViewModel;
import com.example.expensemate.viewmodel.RecurringPaymentsViewModel;
import com.example.expensemate.viewmodel.TransactionViewModel;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class RecurringPaymentsFragment extends Fragment {
    private RecurringPaymentsViewModel viewModel;
    private AccountViewModel accountViewModel;
    private TransactionViewModel transactionViewModel;
    private RecyclerView recyclerView;
    private RecurringPaymentsAdapter adapter;
    private DatePickerHelper expiryDatePicker;
    private ImageButton selectAllButton;
    private TextView totalAmountTextView;
    private TextView remainingAmountTextView;
    private TextView selectAllTextView;
    private boolean isAllSelected = false;
    private List<Account> accountsList = new ArrayList<>();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(RecurringPaymentsViewModel.class);
        accountViewModel = new ViewModelProvider(requireActivity()).get(AccountViewModel.class);
        transactionViewModel = new ViewModelProvider(requireActivity()).get(TransactionViewModel.class);
        expiryDatePicker = new DatePickerHelper(requireContext());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_recurring_payments, container, false);
        
        recyclerView = view.findViewById(R.id.recurring_payments_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new RecurringPaymentsAdapter();
        adapter.setOnPaymentClickListener(new RecurringPaymentsAdapter.OnPaymentClickListener() {
            @Override
            public void onPaymentClick(RecurringPayment payment) {
                showEditDialog(payment);
            }

            @Override
            public void onPaymentStatusChanged(RecurringPayment payment, boolean isCompleted) {
                if (isCompleted) {
                    viewModel.markAsCompleted(payment);
                } else {
                    viewModel.resetCompletionStatus(payment);
                }
            }

            @Override
            public void onSelfTransferClick(RecurringPayment payment) {
                showSelfTransferConfirmation(payment);
            }
        });
        adapter.setFragment(this);
        recyclerView.setAdapter(adapter);

        selectAllButton = view.findViewById(R.id.btn_select_all);
        selectAllTextView = view.findViewById(R.id.tv_select_all);
        totalAmountTextView = view.findViewById(R.id.tv_total_amount);
        remainingAmountTextView = view.findViewById(R.id.tv_remaining_amount);
        selectAllButton.setOnClickListener(v -> toggleSelectAll());

        FloatingActionButton fab = view.findViewById(R.id.fab_add_recurring_payment);
        fab.setOnClickListener(v -> showAddDialog());

        // Observe recurring payments
        viewModel.getRecurringPayments().observe(getViewLifecycleOwner(), payments -> {
            adapter.submitList(payments);
            updateUI();
        });

        // Observe total amount
        viewModel.getTotalAmount().observe(getViewLifecycleOwner(), total -> {
            if (total != null) {
                totalAmountTextView.setText(String.format(Locale.getDefault(), "Total: ₹%.2f", total));
            }
        });

        // Observe remaining amount
        viewModel.getRemainingAmount().observe(getViewLifecycleOwner(), remaining -> {
            if (remaining != null) {
                remainingAmountTextView.setText(String.format(Locale.getDefault(), "Remaining: ₹%.2f", remaining));
            }
        });

        // Observe accounts to build the name map for the adapter
        accountViewModel.getAllAccounts().observe(getViewLifecycleOwner(), accounts -> {
            accountsList = accounts != null ? accounts : new ArrayList<>();
            Map<Long, String> accountNameMap = new HashMap<>();
            for (Account account : accountsList) {
                accountNameMap.put(account.getId(), account.getName());
            }
            adapter.setAccountNameMap(accountNameMap);
        });

        return view;
    }

    private void setupAccountDropdowns(DialogEditRecurringPaymentBinding dialogBinding,
                                        Long initialFromAccountId, Long initialToAccountId) {
        accountViewModel.getAllAccounts().observe(getViewLifecycleOwner(), accounts -> {
            if (accounts == null) return;
            accountsList = accounts;

            List<String> accountNames = new ArrayList<>();
            accountNames.add("None"); // Allow clearing the selection
            for (Account account : accounts) {
                accountNames.add(account.getName());
            }

            ArrayAdapter<String> fromAdapter = new ArrayAdapter<String>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                accountNames
            ) {
                @Override
                public View getView(int position, View convertView, ViewGroup parent) {
                    View view = super.getView(position, convertView, parent);
                    TextView text = (TextView) view.findViewById(android.R.id.text1);
                    text.setTextColor(requireContext().getResources().getColor(R.color.black));
                    return view;
                }
            };

            ArrayAdapter<String> toAdapter = new ArrayAdapter<String>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                accountNames
            ) {
                @Override
                public View getView(int position, View convertView, ViewGroup parent) {
                    View view = super.getView(position, convertView, parent);
                    TextView text = (TextView) view.findViewById(android.R.id.text1);
                    text.setTextColor(requireContext().getResources().getColor(R.color.black));
                    return view;
                }
            };

            dialogBinding.etFromAccount.setAdapter(fromAdapter);
            dialogBinding.etToAccount.setAdapter(toAdapter);

            // Pre-select accounts if editing
            if (initialFromAccountId != null) {
                for (Account account : accounts) {
                    if (account.getId() == initialFromAccountId) {
                        dialogBinding.etFromAccount.setText(account.getName(), false);
                        break;
                    }
                }
            }
            if (initialToAccountId != null) {
                for (Account account : accounts) {
                    if (account.getId() == initialToAccountId) {
                        dialogBinding.etToAccount.setText(account.getName(), false);
                        break;
                    }
                }
            }
        });

        // Show dropdown when clicked
        dialogBinding.etFromAccount.setOnClickListener(v -> {
            dialogBinding.etFromAccount.showDropDown();
        });
        dialogBinding.etToAccount.setOnClickListener(v -> {
            dialogBinding.etToAccount.showDropDown();
        });
        dialogBinding.etFromAccount.setDropDownBackgroundResource(android.R.color.white);
        dialogBinding.etToAccount.setDropDownBackgroundResource(android.R.color.white);
    }

    private Long getSelectedAccountId(AutoCompleteTextView autoCompleteTextView) {
        String selectedName = autoCompleteTextView.getText().toString().trim();
        if (selectedName.isEmpty() || selectedName.equals("None")) {
            return null;
        }
        for (Account account : accountsList) {
            if (account.getName().equals(selectedName)) {
                return account.getId();
            }
        }
        return null;
    }

    private void showAddDialog() {
        DialogEditRecurringPaymentBinding dialogBinding = DialogEditRecurringPaymentBinding.inflate(getLayoutInflater());

        // Set up expiry date picker click listener
        dialogBinding.etExpiryDate.setOnClickListener(v -> expiryDatePicker.showDatePicker(dialogBinding.etExpiryDate, null));

        // Set up account dropdowns
        setupAccountDropdowns(dialogBinding, null, null);

        BaseDialogHelper dialogHelper = new BaseDialogHelper(
                requireContext(),
                "Add Recurring Payment",
                dialogBinding.getRoot(),
                "Add",
                "Cancel",
                new BaseDialogHelper.OnDialogButtonClickListener() {
                    @Override
                    public void onPositiveButtonClick(AlertDialog dialog) {
                        String name = dialogBinding.etPaymentName.getText().toString().trim();
                        String amountStr = dialogBinding.etAmount.getText().toString().trim();
                        String dueDayStr = dialogBinding.etDueDate.getText().toString().trim();
                        Date expiryDate = expiryDatePicker.getSelectedDate();

                        if (name.isEmpty() || amountStr.isEmpty() || dueDayStr.isEmpty() || expiryDate == null) {
                            Toast.makeText(requireContext(), "Please fill all fields", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        try {
                            double amount = Double.parseDouble(amountStr);
                            int dueDay = Integer.parseInt(dueDayStr);
                            
                            if (dueDay < 1 || dueDay > 31) {
                                Toast.makeText(requireContext(), "Due day must be between 1 and 31", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            
                            RecurringPayment payment = new RecurringPayment(name, amount, dueDay, expiryDate);
                            payment.setFromAccountId(getSelectedAccountId(dialogBinding.etFromAccount));
                            payment.setToAccountId(getSelectedAccountId(dialogBinding.etToAccount));
                            viewModel.insert(payment);
                            Toast.makeText(requireContext(), "Payment added", Toast.LENGTH_SHORT).show();
                            dialog.dismiss();
                        } catch (NumberFormatException e) {
                            Toast.makeText(requireContext(), "Invalid amount or due day", Toast.LENGTH_SHORT).show();
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

    private void showEditDialog(RecurringPayment payment) {
        DialogEditRecurringPaymentBinding dialogBinding = DialogEditRecurringPaymentBinding.inflate(getLayoutInflater());

        // Pre-fill the fields
        dialogBinding.etPaymentName.setText(payment.getName());
        dialogBinding.etAmount.setText(String.valueOf(payment.getAmount()));
        dialogBinding.etDueDate.setText(String.valueOf(payment.getDueDay()));
        
        // Set up expiry date picker with initial date
        expiryDatePicker.setSelectedDate(payment.getExpiryDate());
        
        // Set initial expiry date text
        dialogBinding.etExpiryDate.setText(expiryDatePicker.getSelectedDate() != null ? 
            new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(expiryDatePicker.getSelectedDate()) : "");

        // Set up expiry date picker click listener
        dialogBinding.etExpiryDate.setOnClickListener(v -> expiryDatePicker.showDatePicker(dialogBinding.etExpiryDate, payment.getExpiryDate()));

        // Set up account dropdowns with pre-selected values
        setupAccountDropdowns(dialogBinding, payment.getFromAccountId(), payment.getToAccountId());

        BaseDialogHelper dialogHelper = new BaseDialogHelper(
                requireContext(),
                "Edit Recurring Payment",
                dialogBinding.getRoot(),
                "Save",
                "Cancel",
                new BaseDialogHelper.OnDialogButtonClickListener() {
                    @Override
                    public void onPositiveButtonClick(AlertDialog dialog) {
                        String name = dialogBinding.etPaymentName.getText().toString().trim();
                        String amountStr = dialogBinding.etAmount.getText().toString().trim();
                        String dueDayStr = dialogBinding.etDueDate.getText().toString().trim();
                        Date expiryDate = expiryDatePicker.getSelectedDate();

                        if (name.isEmpty() || amountStr.isEmpty() || dueDayStr.isEmpty() || expiryDate == null) {
                            Toast.makeText(requireContext(), "Please fill all fields", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        try {
                            double amount = Double.parseDouble(amountStr);
                            int dueDay = Integer.parseInt(dueDayStr);
                            
                            if (dueDay < 1 || dueDay > 31) {
                                Toast.makeText(requireContext(), "Due day must be between 1 and 31", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            
                            // Create a new payment object with updated values
                            RecurringPayment updatedPayment = new RecurringPayment(name, amount, dueDay, expiryDate);
                            updatedPayment.setId(payment.getId());
                            updatedPayment.setCompleted(payment.isCompleted());
                            updatedPayment.setLastCompletedDate(payment.getLastCompletedDate());
                            updatedPayment.setFromAccountId(getSelectedAccountId(dialogBinding.etFromAccount));
                            updatedPayment.setToAccountId(getSelectedAccountId(dialogBinding.etToAccount));
                            
                            viewModel.update(updatedPayment);
                            Toast.makeText(requireContext(), "Payment updated", Toast.LENGTH_SHORT).show();
                            dialog.dismiss();
                        } catch (NumberFormatException e) {
                            Toast.makeText(requireContext(), "Invalid amount or due day", Toast.LENGTH_SHORT).show();
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

    private void showSelfTransferConfirmation(RecurringPayment payment) {
        // Validate that both accounts are assigned
        if (payment.getFromAccountId() == null || payment.getToAccountId() == null) {
            Toast.makeText(requireContext(),
                    "Both From Account and To Account must be assigned to perform a self transfer.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        if (payment.getFromAccountId().equals(payment.getToAccountId())) {
            Toast.makeText(requireContext(),
                    "From Account and To Account must be different.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // Resolve account names
        String fromAccountName = null;
        String toAccountName = null;
        for (Account account : accountsList) {
            if (account.getId() == payment.getFromAccountId()) {
                fromAccountName = account.getName();
            }
            if (account.getId() == payment.getToAccountId()) {
                toAccountName = account.getName();
            }
        }

        if (fromAccountName == null || toAccountName == null) {
            Toast.makeText(requireContext(),
                    "Could not resolve account names. Please re-assign accounts.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        String message = String.format(Locale.getDefault(),
                "Transfer ₹%.2f from %s to %s?",
                payment.getAmount(), fromAccountName, toAccountName);

        BaseDialogHelper dialogHelper = new BaseDialogHelper(
                requireContext(),
                "Confirm Self Transfer",
                null,
                "Transfer",
                "Cancel",
                new BaseDialogHelper.OnDialogButtonClickListener() {
                    @Override
                    public void onPositiveButtonClick(AlertDialog dialog) {
                        performSelfTransfer(payment);
                        dialog.dismiss();
                    }

                    @Override
                    public void onNegativeButtonClick(AlertDialog dialog) {
                        dialog.dismiss();
                    }
                }
        );

        dialogHelper.setMessage(message);
        dialogHelper.create().show();
    }

    private void performSelfTransfer(RecurringPayment payment) {
        Date now = new Date();
        String description = "Self Transfer: " + payment.getName();

        // Create debit transaction (from account)
        Transaction debitTransaction = new Transaction(
            payment.getAmount(),
            description,
            now,
            "DEBIT",
            "",
            "",
            ""
        );
        debitTransaction.setCategory("Default");
        debitTransaction.setAccountId(payment.getFromAccountId());
        debitTransaction.setLinkedRecurringPaymentId(payment.getId());

        // Create credit transaction (to account)
        Transaction creditTransaction = new Transaction(
            payment.getAmount(),
            description,
            now,
            "CREDIT",
            "",
            "",
            ""
        );
        creditTransaction.setCategory("Default");
        creditTransaction.setAccountId(payment.getToAccountId());

        // Insert both transactions
        transactionViewModel.insertTransaction(debitTransaction);
        transactionViewModel.insertTransaction(creditTransaction);

        // Mark the recurring payment as completed
        viewModel.markAsCompleted(payment);

        Toast.makeText(requireContext(), "Self transfer completed successfully", Toast.LENGTH_SHORT).show();
    }

    public void showDeleteConfirmationDialog(RecurringPayment payment) {
        BaseDialogHelper dialogHelper = new BaseDialogHelper(
                requireContext(),
                "Delete Payment",
                null,
                "Delete",
                "Cancel",
                new BaseDialogHelper.OnDialogButtonClickListener() {
                    @Override
                    public void onPositiveButtonClick(AlertDialog dialog) {
                        viewModel.delete(payment);
                        Toast.makeText(requireContext(), "Payment deleted", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    }

                    @Override
                    public void onNegativeButtonClick(AlertDialog dialog) {
                        dialog.dismiss();
                    }
                }
        );

        dialogHelper.setMessage("Are you sure you want to delete this payment?");
        dialogHelper.create().show();
    }

    private void toggleSelectAll() {
        isAllSelected = !isAllSelected;
        adapter.setAllSelected(isAllSelected);
        selectAllTextView.setText(isAllSelected ? "Deselect All" : "Select All");
    }

    private void updateUI() {
        List<RecurringPayment> payments = viewModel.getRecurringPayments().getValue();
        if (payments != null) {
            boolean allCompleted = true;
            for (RecurringPayment payment : payments) {
                if (!payment.isCompleted()) {
                    allCompleted = false;
                    break;
                }
            }
            isAllSelected = allCompleted;
            selectAllTextView.setText(isAllSelected ? "Deselect All" : "Select All");
        }
    }
}