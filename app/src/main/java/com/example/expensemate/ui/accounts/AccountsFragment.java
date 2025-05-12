package com.example.expensemate.ui.accounts;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.expensemate.R;
import com.example.expensemate.data.Account;
import com.example.expensemate.viewmodel.AccountViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class AccountsFragment extends Fragment implements AccountsAdapter.OnAccountClickListener {
    private AccountViewModel accountViewModel;
    private AccountsAdapter adapter;
    private SimpleDateFormat dateFormat;
    private Calendar calendar;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        calendar = Calendar.getInstance();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_accounts, container, false);

        RecyclerView recyclerView = view.findViewById(R.id.rvAccounts);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new AccountsAdapter(this);
        recyclerView.setAdapter(adapter);

        FloatingActionButton fabAddAccount = view.findViewById(R.id.fabAddAccount);
        fabAddAccount.setOnClickListener(v -> showAddAccountDialog());

        accountViewModel = new ViewModelProvider(this).get(AccountViewModel.class);
        accountViewModel.getAllAccounts().observe(getViewLifecycleOwner(), accounts -> {
            adapter.submitList(accounts);
        });

        accountViewModel.getDefaultAccount().observe(getViewLifecycleOwner(), defaultAccount -> {
            adapter.setDefaultAccount(defaultAccount);
        });

        return view;
    }

    private void showAddAccountDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_account, null);
        TextInputLayout nameLayout = dialogView.findViewById(R.id.tilAccountName);
        TextInputLayout numberLayout = dialogView.findViewById(R.id.tilAccountNumber);
        TextInputLayout bankLayout = dialogView.findViewById(R.id.tilBank);
        TextInputLayout expiryLayout = dialogView.findViewById(R.id.tilExpiryDate);
        TextInputLayout descriptionLayout = dialogView.findViewById(R.id.tilDescription);

        TextInputEditText nameInput = dialogView.findViewById(R.id.etAccountName);
        TextInputEditText numberInput = dialogView.findViewById(R.id.etAccountNumber);
        TextInputEditText bankInput = dialogView.findViewById(R.id.etBank);
        TextInputEditText expiryInput = dialogView.findViewById(R.id.etExpiryDate);
        TextInputEditText descriptionInput = dialogView.findViewById(R.id.etDescription);

        expiryInput.setOnClickListener(v -> showDatePicker(expiryInput));

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Add Account")
                .setView(dialogView)
                .setPositiveButton("Save", (dialog, which) -> {
                    String name = nameInput.getText().toString().trim();
                    if (name.isEmpty()) {
                        nameLayout.setError("Name is required");
                        return;
                    }

                    String number = numberInput.getText().toString().trim();
                    String bank = bankInput.getText().toString().trim();
                    String description = descriptionInput.getText().toString().trim();
                    Date expiryDate = null;
                    try {
                        String expiryStr = expiryInput.getText().toString().trim();
                        if (!expiryStr.isEmpty()) {
                            expiryDate = dateFormat.parse(expiryStr);
                        }
                    } catch (Exception e) {
                        expiryLayout.setError("Invalid date format");
                        return;
                    }

                    Account account = new Account(name, number, bank, expiryDate, description);
                    accountViewModel.insert(account);
                    Toast.makeText(getContext(), "Account added", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showEditAccountDialog(Account account) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_account, null);
        TextInputLayout nameLayout = dialogView.findViewById(R.id.tilAccountName);
        TextInputLayout numberLayout = dialogView.findViewById(R.id.tilAccountNumber);
        TextInputLayout bankLayout = dialogView.findViewById(R.id.tilBank);
        TextInputLayout expiryLayout = dialogView.findViewById(R.id.tilExpiryDate);
        TextInputLayout descriptionLayout = dialogView.findViewById(R.id.tilDescription);

        TextInputEditText nameInput = dialogView.findViewById(R.id.etAccountName);
        TextInputEditText numberInput = dialogView.findViewById(R.id.etAccountNumber);
        TextInputEditText bankInput = dialogView.findViewById(R.id.etBank);
        TextInputEditText expiryInput = dialogView.findViewById(R.id.etExpiryDate);
        TextInputEditText descriptionInput = dialogView.findViewById(R.id.etDescription);

        nameInput.setText(account.getName());
        numberInput.setText(account.getAccountNumber());
        bankInput.setText(account.getBank());
        if (account.getExpiryDate() != null) {
            expiryInput.setText(dateFormat.format(account.getExpiryDate()));
        }
        descriptionInput.setText(account.getDescription());

        expiryInput.setOnClickListener(v -> showDatePicker(expiryInput));

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Edit Account")
                .setView(dialogView)
                .setPositiveButton("Save", (dialog, which) -> {
                    String name = nameInput.getText().toString().trim();
                    if (name.isEmpty()) {
                        nameLayout.setError("Name is required");
                        return;
                    }

                    String number = numberInput.getText().toString().trim();
                    String bank = bankInput.getText().toString().trim();
                    String description = descriptionInput.getText().toString().trim();
                    Date expiryDate = null;
                    try {
                        String expiryStr = expiryInput.getText().toString().trim();
                        if (!expiryStr.isEmpty()) {
                            expiryDate = dateFormat.parse(expiryStr);
                        }
                    } catch (Exception e) {
                        expiryLayout.setError("Invalid date format");
                        return;
                    }

                    account.setName(name);
                    account.setAccountNumber(number);
                    account.setBank(bank);
                    account.setExpiryDate(expiryDate);
                    account.setDescription(description);
                    accountViewModel.update(account);
                    Toast.makeText(getContext(), "Account updated", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showDatePicker(TextInputEditText expiryInput) {
        DatePickerDialog datePickerDialog = new DatePickerDialog(
                requireContext(),
                (view, year, month, dayOfMonth) -> {
                    calendar.set(year, month, dayOfMonth);
                    expiryInput.setText(dateFormat.format(calendar.getTime()));
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );
        datePickerDialog.show();
    }

    @Override
    public void onEditClick(Account account) {
        showEditAccountDialog(account);
    }

    @Override
    public void onSetDefaultClick(Account account) {
        accountViewModel.setDefaultAccount(account.getId());
        Toast.makeText(getContext(), "Default account updated", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDeleteClick(Account account) {
        if (account.isDefault()) {
            Toast.makeText(getContext(), "Cannot delete default account", Toast.LENGTH_SHORT).show();
            return;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete Account")
                .setMessage("Are you sure you want to delete this account?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    accountViewModel.delete(account);
                    Toast.makeText(getContext(), "Account deleted", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
} 