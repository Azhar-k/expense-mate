package com.example.expensemate.ui.recurringpayment;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
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
import java.util.Calendar;
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
    
    private RecyclerView externalRecyclerView;
    private RecyclerView internalRecyclerView;
    private RecurringPaymentsAdapter externalAdapter;
    private RecurringPaymentsAdapter internalAdapter;
    
    private DatePickerHelper expiryDatePicker;
    private ImageButton selectAllButton;
    private TextView selectAllTextView;
    
    private TextView overallTotalTextView;
    private TextView overallRemainingTextView;
    private TextView externalTotalTextView;
    private TextView externalRemainingTextView;
    private TextView internalTotalTextView;
    private TextView internalRemainingTextView;
    private TextView emptyExternalTextView;
    private TextView emptyInternalTextView;
    
    private ImageButton transferAllInternalButton;
    private TextView transferAllInternalTextView;

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
        
        // Find views
        overallTotalTextView = view.findViewById(R.id.tv_total_amount);
        overallRemainingTextView = view.findViewById(R.id.tv_remaining_amount);
        selectAllButton = view.findViewById(R.id.btn_select_all);
        selectAllTextView = view.findViewById(R.id.tv_select_all);
        
        externalTotalTextView = view.findViewById(R.id.tv_external_total_amount);
        externalRemainingTextView = view.findViewById(R.id.tv_external_remaining_amount);
        emptyExternalTextView = view.findViewById(R.id.tv_empty_external);
        externalRecyclerView = view.findViewById(R.id.rv_external_recurring_payments);
        
        internalTotalTextView = view.findViewById(R.id.tv_internal_total_amount);
        internalRemainingTextView = view.findViewById(R.id.tv_internal_remaining_amount);
        emptyInternalTextView = view.findViewById(R.id.tv_empty_internal);
        internalRecyclerView = view.findViewById(R.id.rv_internal_recurring_payments);
        
        transferAllInternalButton = view.findViewById(R.id.btn_transfer_all_internal);
        transferAllInternalTextView = view.findViewById(R.id.tv_transfer_all_internal);

        // Setup common click listener
        RecurringPaymentsAdapter.OnPaymentClickListener paymentClickListener = new RecurringPaymentsAdapter.OnPaymentClickListener() {
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
        };

        // External RecyclerView setup
        externalRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        externalAdapter = new RecurringPaymentsAdapter();
        externalAdapter.setOnPaymentClickListener(paymentClickListener);
        externalAdapter.setFragment(this);
        externalRecyclerView.setAdapter(externalAdapter);

        // Internal RecyclerView setup
        internalRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        internalAdapter = new RecurringPaymentsAdapter();
        internalAdapter.setOnPaymentClickListener(paymentClickListener);
        internalAdapter.setFragment(this);
        internalRecyclerView.setAdapter(internalAdapter);

        selectAllButton.setOnClickListener(v -> toggleSelectAll());

        View.OnClickListener bulkTransferClickListener = v -> showBulkInternalTransferConfirmation();
        if (transferAllInternalButton != null) {
            transferAllInternalButton.setOnClickListener(bulkTransferClickListener);
        }
        if (transferAllInternalTextView != null) {
            transferAllInternalTextView.setOnClickListener(bulkTransferClickListener);
        }

        FloatingActionButton fab = view.findViewById(R.id.fab_add_recurring_payment);
        fab.setOnClickListener(v -> showAddDialog());

        // Observe recurring payments and split into External and Internal
        viewModel.getRecurringPayments().observe(getViewLifecycleOwner(), payments -> {
            List<RecurringPayment> externalList = new ArrayList<>();
            List<RecurringPayment> internalList = new ArrayList<>();
            
            double extTotal = 0, extRemaining = 0;
            double intTotal = 0, intRemaining = 0;
            
            if (payments != null) {
                for (RecurringPayment p : payments) {
                    boolean isInternal = p.getFromAccountId() != null && p.getToAccountId() != null;
                    if (isInternal) {
                        internalList.add(p);
                        intTotal += p.getAmount();
                        if (!p.isCompleted()) {
                            intRemaining += p.getAmount();
                        }
                    } else {
                        externalList.add(p);
                        extTotal += p.getAmount();
                        if (!p.isCompleted()) {
                            extRemaining += p.getAmount();
                        }
                    }
                }
            }
            
            // Submit lists to adapters
            externalAdapter.submitList(externalList);
            internalAdapter.submitList(internalList);
            
            // Update section visibility
            emptyExternalTextView.setVisibility(externalList.isEmpty() ? View.VISIBLE : View.GONE);
            emptyInternalTextView.setVisibility(internalList.isEmpty() ? View.VISIBLE : View.GONE);
            
            // Update section totals
            externalTotalTextView.setText(String.format(Locale.getDefault(), "Total: ₹%.2f", extTotal));
            externalRemainingTextView.setText(String.format(Locale.getDefault(), "Remaining: ₹%.2f", extRemaining));
            
            internalTotalTextView.setText(String.format(Locale.getDefault(), "Total: ₹%.2f", intTotal));
            internalRemainingTextView.setText(String.format(Locale.getDefault(), "Remaining: ₹%.2f", intRemaining));
            
            // Update overall totals
            double overallTotal = extTotal + intTotal;
            double overallRemaining = extRemaining + intRemaining;
            overallTotalTextView.setText(String.format(Locale.getDefault(), "Total: ₹%.2f", overallTotal));
            overallRemainingTextView.setText(String.format(Locale.getDefault(), "Remaining: ₹%.2f", overallRemaining));
            
            updateSelectAllButtonState(payments);
        });

        // Observe accounts to build the name map for the adapters
        accountViewModel.getAllAccounts().observe(getViewLifecycleOwner(), accounts -> {
            accountsList = accounts != null ? accounts : new ArrayList<>();
            Map<Long, String> accountNameMap = new HashMap<>();
            for (Account account : accountsList) {
                accountNameMap.put(account.getId(), account.getName());
            }
            externalAdapter.setAccountNameMap(accountNameMap);
            internalAdapter.setAccountNameMap(accountNameMap);
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
        
        dialogBinding.etPaymentName.setText(payment.getName());
        dialogBinding.etAmount.setText(String.valueOf(payment.getAmount()));
        dialogBinding.etDueDate.setText(String.valueOf(payment.getDueDay()));
        
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        dialogBinding.etExpiryDate.setText(sdf.format(payment.getExpiryDate()));
        expiryDatePicker.setSelectedDate(payment.getExpiryDate());

        // Set up expiry date picker click listener
        dialogBinding.etExpiryDate.setOnClickListener(v -> expiryDatePicker.showDatePicker(dialogBinding.etExpiryDate, payment.getExpiryDate()));

        // Set up account dropdowns with existing values
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
                            
                            payment.setName(name);
                            payment.setAmount(amount);
                            payment.setDueDay(dueDay);
                            payment.setExpiryDate(expiryDate);
                            payment.setFromAccountId(getSelectedAccountId(dialogBinding.etFromAccount));
                            payment.setToAccountId(getSelectedAccountId(dialogBinding.etToAccount));
                            viewModel.update(payment);
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
        Long fromId = payment.getFromAccountId();
        Long toId = payment.getToAccountId();

        if (fromId == null || toId == null) {
            Toast.makeText(requireContext(), "Both From Account and To Account must be assigned to make a self transfer", Toast.LENGTH_LONG).show();
            return;
        }

        if (fromId.equals(toId)) {
            Toast.makeText(requireContext(), "From Account and To Account cannot be the same", Toast.LENGTH_LONG).show();
            return;
        }

        String fromName = getAccountName(fromId);
        String toName = getAccountName(toId);

        String message = String.format(Locale.getDefault(),
                "Transfer ₹%.2f from '%s' to '%s' for '%s'?",
                payment.getAmount(), fromName, toName, payment.getName());

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

    private String getAccountName(Long accountId) {
        for (Account account : accountsList) {
            if (account.getId() == accountId) {
                return account.getName();
            }
        }
        return "Unknown Account";
    }

    private void executeSelfTransfer(RecurringPayment payment) {
        long fromId = payment.getFromAccountId();
        long toId = payment.getToAccountId();
        double amount = payment.getAmount();
        Date now = new Date();

        // 1. Create DEBIT transaction for From Account (linked to recurring payment)
        Transaction debitTransaction = new Transaction();
        debitTransaction.setAmount(amount);
        debitTransaction.setTransactionType("DEBIT");
        debitTransaction.setCategory("Default");
        debitTransaction.setDate(now);
        debitTransaction.setDescription("Self Transfer: " + payment.getName());
        debitTransaction.setAccountId(fromId);
        debitTransaction.setExcludedFromSummary(false);
        debitTransaction.setLinkedRecurringPaymentId(payment.getId());

        // 2. Create CREDIT transaction for To Account
        Transaction creditTransaction = new Transaction();
        creditTransaction.setAmount(amount);
        creditTransaction.setTransactionType("CREDIT");
        creditTransaction.setCategory("Default");
        creditTransaction.setDate(now);
        creditTransaction.setDescription("Self Transfer: " + payment.getName());
        creditTransaction.setAccountId(toId);
        creditTransaction.setExcludedFromSummary(false);

        // Insert both transactions
        transactionViewModel.insertTransaction(debitTransaction);
        transactionViewModel.insertTransaction(creditTransaction);

        // Mark the recurring payment as completed
        viewModel.markAsCompleted(payment);
    }

    private void performSelfTransfer(RecurringPayment payment) {
        executeSelfTransfer(payment);
        Toast.makeText(requireContext(), "Self transfer completed successfully", Toast.LENGTH_SHORT).show();
    }

    private void showBulkInternalTransferConfirmation() {
        List<RecurringPayment> payments = viewModel.getRecurringPayments().getValue();
        List<RecurringPayment> eligiblePayments = new ArrayList<>();
        if (payments != null) {
            Calendar calendar = Calendar.getInstance();
            Date today = calendar.getTime();
            for (RecurringPayment p : payments) {
                boolean isInternal = p.getFromAccountId() != null && p.getToAccountId() != null;
                boolean isExpired = p.getExpiryDate().before(today);
                if (isInternal && !p.isCompleted() && !isExpired) {
                    if (!p.getFromAccountId().equals(p.getToAccountId())) {
                        eligiblePayments.add(p);
                    }
                }
            }
        }

        if (eligiblePayments.isEmpty()) {
            Toast.makeText(requireContext(), "No pending internal recurring payments to transfer", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("The following self transfers will be performed:\n\n");
        double totalAmount = 0;
        for (RecurringPayment p : eligiblePayments) {
            String fromName = getAccountName(p.getFromAccountId());
            String toName = getAccountName(p.getToAccountId());
            sb.append(String.format(Locale.getDefault(), "• %s (₹%.2f): %s → %s\n",
                    p.getName(), p.getAmount(), fromName, toName));
            totalAmount += p.getAmount();
        }
        sb.append(String.format(Locale.getDefault(), "\nTotal Amount: ₹%.2f", totalAmount));

        BaseDialogHelper dialogHelper = new BaseDialogHelper(
                requireContext(),
                "Confirm Bulk Self Transfer",
                null,
                "Transfer All",
                "Cancel",
                new BaseDialogHelper.OnDialogButtonClickListener() {
                    @Override
                    public void onPositiveButtonClick(AlertDialog dialog) {
                        for (RecurringPayment p : eligiblePayments) {
                            executeSelfTransfer(p);
                        }
                        Toast.makeText(requireContext(), eligiblePayments.size() + " self transfers completed successfully", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    }

                    @Override
                    public void onNegativeButtonClick(AlertDialog dialog) {
                        dialog.dismiss();
                    }
                }
        );

        dialogHelper.setMessage(sb.toString());
        dialogHelper.create().show();
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
        externalAdapter.setAllSelected(isAllSelected);
        internalAdapter.setAllSelected(isAllSelected);
        selectAllTextView.setText(isAllSelected ? "Deselect All" : "Select All");
    }

    private void updateSelectAllButtonState(List<RecurringPayment> payments) {
        if (payments != null && !payments.isEmpty()) {
            boolean allCompleted = true;
            for (RecurringPayment payment : payments) {
                if (!payment.isCompleted()) {
                    allCompleted = false;
                    break;
                }
            }
            isAllSelected = allCompleted;
            selectAllTextView.setText(isAllSelected ? "Deselect All" : "Select All");
        } else {
            isAllSelected = false;
            selectAllTextView.setText("Select All");
        }
    }
}