package com.example.expensemate.ui.recurringpayment;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import com.example.expensemate.data.RecurringPayment;
import com.example.expensemate.databinding.DialogEditRecurringPaymentBinding;
import com.example.expensemate.ui.DatePickerHelper;
import com.example.expensemate.ui.common.BaseDialogHelper;
import com.example.expensemate.viewmodel.RecurringPaymentsViewModel;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class RecurringPaymentsFragment extends Fragment {
    private RecurringPaymentsViewModel viewModel;
    private RecyclerView recyclerView;
    private RecurringPaymentsAdapter adapter;
    private DatePickerHelper expiryDatePicker;
    private ImageButton selectAllButton;
    private TextView totalAmountTextView;
    private TextView remainingAmountTextView;
    private TextView selectAllTextView;
    private boolean isAllSelected = false;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(RecurringPaymentsViewModel.class);
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

        return view;
    }

    private void showAddDialog() {
        DialogEditRecurringPaymentBinding dialogBinding = DialogEditRecurringPaymentBinding.inflate(getLayoutInflater());

        // Set up expiry date picker click listener
        dialogBinding.etExpiryDate.setOnClickListener(v -> expiryDatePicker.showDatePicker(dialogBinding.etExpiryDate, null));

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