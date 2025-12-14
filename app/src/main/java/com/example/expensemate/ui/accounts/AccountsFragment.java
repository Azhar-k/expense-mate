package com.example.expensemate.ui.accounts;

import android.app.DatePickerDialog;
import android.app.AlertDialog;
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
import com.example.expensemate.databinding.DialogEditAccountBinding;
import com.example.expensemate.ui.common.BaseDialogHelper;
import com.example.expensemate.viewmodel.AccountViewModel;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;

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
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
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
        DialogEditAccountBinding dialogBinding = DialogEditAccountBinding.inflate(getLayoutInflater());

        dialogBinding.etExpiryDate.setOnClickListener(v -> showDatePicker(dialogBinding.etExpiryDate));

        BaseDialogHelper dialogHelper = new BaseDialogHelper(
                requireContext(),
                "Add Account",
                dialogBinding.getRoot(),
                "Save",
                "Cancel",
                new BaseDialogHelper.OnDialogButtonClickListener() {
                    @Override
                    public void onPositiveButtonClick(AlertDialog dialog) {
                        String name = dialogBinding.etAccountName.getText().toString().trim();
                        if (name.isEmpty()) {
                            dialogBinding.tilAccountName.setError("Name is required");
                            return;
                        }

                        String number = dialogBinding.etAccountNumber.getText().toString().trim();
                        String bank = dialogBinding.etBank.getText().toString().trim();
                        String description = dialogBinding.etDescription.getText().toString().trim();
                        Date expiryDate = null;
                        try {
                            String expiryStr = dialogBinding.etExpiryDate.getText().toString().trim();
                            if (!expiryStr.isEmpty()) {
                                expiryDate = dateFormat.parse(expiryStr);
                            }
                        } catch (Exception e) {
                            dialogBinding.tilExpiryDate.setError("Invalid date format");
                            return;
                        }

                        Account account = new Account(name, number, bank, expiryDate, description);
                        accountViewModel.insert(account);
                        Toast.makeText(getContext(), "Account added", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    }

                    @Override
                    public void onNegativeButtonClick(AlertDialog dialog) {
                        dialog.dismiss();
                    }
                });

        dialogHelper.create().show();
    }

    private void showEditAccountDialog(Account account) {
        DialogEditAccountBinding dialogBinding = DialogEditAccountBinding.inflate(getLayoutInflater());

        dialogBinding.etAccountName.setText(account.getName());
        dialogBinding.etAccountNumber.setText(account.getAccountNumber());
        dialogBinding.etBank.setText(account.getBank());
        if (account.getExpiryDate() != null) {
            dialogBinding.etExpiryDate.setText(dateFormat.format(account.getExpiryDate()));
        }
        dialogBinding.etDescription.setText(account.getDescription());

        dialogBinding.etExpiryDate.setOnClickListener(v -> showDatePicker(dialogBinding.etExpiryDate));

        BaseDialogHelper dialogHelper = new BaseDialogHelper(
                requireContext(),
                "Edit Account",
                dialogBinding.getRoot(),
                "Save",
                "Cancel",
                new BaseDialogHelper.OnDialogButtonClickListener() {
                    @Override
                    public void onPositiveButtonClick(AlertDialog dialog) {
                        String name = dialogBinding.etAccountName.getText().toString().trim();
                        if (name.isEmpty()) {
                            dialogBinding.tilAccountName.setError("Name is required");
                            return;
                        }

                        String number = dialogBinding.etAccountNumber.getText().toString().trim();
                        String bank = dialogBinding.etBank.getText().toString().trim();
                        String description = dialogBinding.etDescription.getText().toString().trim();
                        Date expiryDate = null;
                        try {
                            String expiryStr = dialogBinding.etExpiryDate.getText().toString().trim();
                            if (!expiryStr.isEmpty()) {
                                expiryDate = dateFormat.parse(expiryStr);
                            }
                        } catch (Exception e) {
                            dialogBinding.tilExpiryDate.setError("Invalid date format");
                            return;
                        }

                        account.setName(name);
                        account.setAccountNumber(number);
                        account.setBank(bank);
                        account.setExpiryDate(expiryDate);
                        account.setDescription(description);
                        accountViewModel.update(account);
                        Toast.makeText(getContext(), "Account updated", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    }

                    @Override
                    public void onNegativeButtonClick(AlertDialog dialog) {
                        dialog.dismiss();
                    }
                });

        dialogHelper.create().show();
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
                calendar.get(Calendar.DAY_OF_MONTH));
        datePickerDialog.show();
    }

    @Override
    public void onEditClick(Account account) {
        showEditAccountDialog(account);
    }

    @Override
    public void onDeleteClick(Account account) {
        if (account.isDefault()) {
            Toast.makeText(getContext(), "Cannot delete default account", Toast.LENGTH_SHORT).show();
            return;
        }

        BaseDialogHelper dialogHelper = new BaseDialogHelper(
                requireContext(),
                "Delete Account",
                null,
                "Delete",
                "Cancel",
                new BaseDialogHelper.OnDialogButtonClickListener() {
                    @Override
                    public void onPositiveButtonClick(AlertDialog dialog) {
                        accountViewModel.delete(account);
                        Toast.makeText(getContext(), "Account deleted", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    }

                    @Override
                    public void onNegativeButtonClick(AlertDialog dialog) {
                        dialog.dismiss();
                    }
                });

        dialogHelper.setMessage("Are you sure you want to delete this account?");
        dialogHelper.create().show();
    }

    @Override
    public void onSetDefaultClick(Account account) {
        accountViewModel.setDefaultAccount(account.getId());
        Toast.makeText(getContext(), "Default account updated", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onAccountClick(Account account) {
        Bundle args = new Bundle();
        args.putLong("accountId", account.getId());
        androidx.navigation.Navigation.findNavController(requireView())
                .navigate(R.id.account_details_fragment, args);
    }
}