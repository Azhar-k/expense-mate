package com.example.expensemate.ui;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.expensemate.R;
import com.example.expensemate.data.RecurringPayment;
import com.example.expensemate.databinding.DialogEditRecurringPaymentBinding;
import com.example.expensemate.viewmodel.RecurringPaymentsViewModel;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.util.Locale;

public class RecurringPaymentsFragment extends Fragment {
    private RecurringPaymentsViewModel viewModel;
    private RecyclerView recyclerView;
    private RecurringPaymentsAdapter adapter;
    private DatePickerHelper dueDatePicker;
    private DatePickerHelper expiryDatePicker;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(RecurringPaymentsViewModel.class);
        dueDatePicker = new DatePickerHelper(requireContext());
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

        FloatingActionButton fab = view.findViewById(R.id.fab_add_recurring_payment);
        fab.setOnClickListener(v -> showAddDialog());

        viewModel.getRecurringPayments().observe(getViewLifecycleOwner(), payments -> {
            adapter.submitList(payments);
        });

        return view;
    }

    private void showAddDialog() {
        DialogEditRecurringPaymentBinding dialogBinding = DialogEditRecurringPaymentBinding.inflate(getLayoutInflater());

        // Set up date picker click listeners
        dialogBinding.etDueDate.setOnClickListener(v -> dueDatePicker.showDatePicker(dialogBinding.etDueDate, null));
        dialogBinding.etExpiryDate.setOnClickListener(v -> expiryDatePicker.showDatePicker(dialogBinding.etExpiryDate, null));

        AlertDialog dialog = new AlertDialog.Builder(requireContext(), 
                com.google.android.material.R.style.Theme_MaterialComponents_Light_Dialog)
                .setTitle("Add Recurring Payment")
                .setView(dialogBinding.getRoot())
                .setPositiveButton("Add", null)
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(dialogInterface -> {
            Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            Button negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            
            positiveButton.setTextColor(requireContext().getResources().getColor(R.color.primary));
            negativeButton.setTextColor(requireContext().getResources().getColor(R.color.primary));
            
            positiveButton.setOnClickListener(v -> {
                String name = dialogBinding.etPaymentName.getText().toString().trim();
                String amountStr = dialogBinding.etAmount.getText().toString().trim();
                Date dueDate = dueDatePicker.getSelectedDate();
                Date expiryDate = expiryDatePicker.getSelectedDate();

                if (name.isEmpty() || amountStr.isEmpty() || dueDate == null || expiryDate == null) {
                    Toast.makeText(requireContext(), "Please fill all fields", Toast.LENGTH_SHORT).show();
                    return;
                }

                try {
                    double amount = Double.parseDouble(amountStr);
                    RecurringPayment payment = new RecurringPayment(name, amount, dueDate, expiryDate);
                    viewModel.insert(payment);
                    Toast.makeText(requireContext(), "Payment added", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                } catch (NumberFormatException e) {
                    Toast.makeText(requireContext(), "Invalid amount", Toast.LENGTH_SHORT).show();
                }
            });
        });

        dialog.show();
    }

    private void showEditDialog(RecurringPayment payment) {
        DialogEditRecurringPaymentBinding dialogBinding = DialogEditRecurringPaymentBinding.inflate(getLayoutInflater());

        // Pre-fill the fields
        dialogBinding.etPaymentName.setText(payment.getName());
        dialogBinding.etAmount.setText(String.valueOf(payment.getAmount()));
        
        // Set up date pickers with initial dates
        dueDatePicker.setSelectedDate(payment.getDueDate());
        expiryDatePicker.setSelectedDate(payment.getExpiryDate());
        
        // Set initial date text
        dialogBinding.etDueDate.setText(dueDatePicker.getSelectedDate() != null ? 
            new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(dueDatePicker.getSelectedDate()) : "");
        dialogBinding.etExpiryDate.setText(expiryDatePicker.getSelectedDate() != null ? 
            new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(expiryDatePicker.getSelectedDate()) : "");

        // Set up date picker click listeners
        dialogBinding.etDueDate.setOnClickListener(v -> dueDatePicker.showDatePicker(dialogBinding.etDueDate, payment.getDueDate()));
        dialogBinding.etExpiryDate.setOnClickListener(v -> expiryDatePicker.showDatePicker(dialogBinding.etExpiryDate, payment.getExpiryDate()));

        AlertDialog dialog = new AlertDialog.Builder(requireContext(), 
                com.google.android.material.R.style.Theme_MaterialComponents_Light_Dialog)
                .setTitle("Edit Recurring Payment")
                .setView(dialogBinding.getRoot())
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(dialogInterface -> {
            Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            Button negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            
            positiveButton.setTextColor(requireContext().getResources().getColor(R.color.primary));
            negativeButton.setTextColor(requireContext().getResources().getColor(R.color.primary));
            
            positiveButton.setOnClickListener(v -> {
                String name = dialogBinding.etPaymentName.getText().toString().trim();
                String amountStr = dialogBinding.etAmount.getText().toString().trim();
                Date dueDate = dueDatePicker.getSelectedDate();
                Date expiryDate = expiryDatePicker.getSelectedDate();

                if (name.isEmpty() || amountStr.isEmpty() || dueDate == null || expiryDate == null) {
                    Toast.makeText(requireContext(), "Please fill all fields", Toast.LENGTH_SHORT).show();
                    return;
                }

                try {
                    double amount = Double.parseDouble(amountStr);
                    payment.setName(name);
                    payment.setAmount(amount);
                    payment.setDueDate(dueDate);
                    payment.setExpiryDate(expiryDate);
                    viewModel.update(payment);
                    Toast.makeText(requireContext(), "Payment updated", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                } catch (NumberFormatException e) {
                    Toast.makeText(requireContext(), "Invalid amount", Toast.LENGTH_SHORT).show();
                }
            });
        });

        dialog.show();
    }

    public void showDeleteConfirmationDialog(RecurringPayment payment) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete Payment")
                .setMessage("Are you sure you want to delete this payment?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    viewModel.delete(payment);
                    Toast.makeText(requireContext(), "Payment deleted", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
} 