package com.example.expensemate.ui.activity;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.expensemate.R;
import com.example.expensemate.data.Account;
import com.example.expensemate.data.Category;
import com.example.expensemate.data.RecurringPayment;
import com.example.expensemate.data.Transaction;
import com.example.expensemate.data.TransactionKeys;
import com.example.expensemate.data.Transfers;
import com.example.expensemate.databinding.SheetTransactionFormBinding;
import com.example.expensemate.ui.common.ExpandedSheet;
import com.example.expensemate.ui.common.Pickers;
import com.example.expensemate.ui.common.Ui;
import com.example.expensemate.util.Money;
import com.example.expensemate.viewmodel.AccountViewModel;
import com.example.expensemate.viewmodel.CategoryViewModel;
import com.example.expensemate.viewmodel.RecurringPaymentsViewModel;
import com.example.expensemate.viewmodel.TransactionViewModel;
import com.google.android.material.chip.Chip;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** The one place to add or edit an expense, an income or a transfer. */
public class TransactionFormSheet extends ExpandedSheet {
    private static final String ARG_EDIT_ID = "edit_id";
    private static final String ARG_AMOUNT = "amount";
    private static final String ARG_NOTE = "note";
    private static final String ARG_MESSAGE = "message";
    private static final String ARG_SENDER = "sender";
    private static final String NO_BILL = "None";

    private SheetTransactionFormBinding binding;
    private TransactionViewModel viewModel;
    private RecurringPaymentsViewModel billsViewModel;

    private final Calendar date = Calendar.getInstance();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault());
    private List<Account> accounts = new ArrayList<>();
    private List<Category> categories = new ArrayList<>();
    private List<RecurringPayment> bills = new ArrayList<>();
    private String selectedCategory;
    private Transaction editing;
    private Transaction editingDebit;
    private Transaction editingCredit;

    public static void showAdd(FragmentActivity activity) {
        new TransactionFormSheet().show(activity.getSupportFragmentManager(), "transaction_form");
    }

    public static void showEdit(FragmentActivity activity, long transactionId) {
        Bundle args = new Bundle();
        args.putLong(ARG_EDIT_ID, transactionId);
        TransactionFormSheet sheet = new TransactionFormSheet();
        sheet.setArguments(args);
        sheet.show(activity.getSupportFragmentManager(), "transaction_form");
    }

    /** Opens Add with what a scanned message could tell us, keeping the message with the transaction. */
    public static void showAddFromMessage(FragmentActivity activity, Double amount, String message, String sender) {
        Bundle args = new Bundle();
        if (amount != null) {
            args.putDouble(ARG_AMOUNT, amount);
        }
        args.putString(ARG_MESSAGE, message);
        args.putString(ARG_NOTE, message);
        args.putString(ARG_SENDER, sender);
        TransactionFormSheet sheet = new TransactionFormSheet();
        sheet.setArguments(args);
        sheet.show(activity.getSupportFragmentManager(), "transaction_form");
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = SheetTransactionFormBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        ViewModelProvider provider = new ViewModelProvider(requireActivity());
        viewModel = provider.get(TransactionViewModel.class);
        billsViewModel = provider.get(RecurringPaymentsViewModel.class);
        AccountViewModel accountViewModel = provider.get(AccountViewModel.class);
        CategoryViewModel categoryViewModel = provider.get(CategoryViewModel.class);

        binding.typeToggle.check(R.id.btnExpense);
        binding.typeToggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                onTypeChanged();
            }
        });
        binding.chipDate.setText(dateFormat.format(date.getTime()));
        binding.chipDate.setOnClickListener(v -> Pickers.pickDateTime(getChildFragmentManager(), date,
                () -> binding.chipDate.setText(dateFormat.format(date.getTime()))));
        binding.chipMore.setOnClickListener(v -> {
            binding.groupMore.setVisibility(View.VISIBLE);
            binding.chipMore.setVisibility(View.GONE);
        });
        binding.etAmount.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                binding.tvAmountError.setVisibility(View.GONE);
                updateSaveLabel();
            }
        });
        binding.btnSave.setOnClickListener(v -> save());
        binding.etBill.setText(NO_BILL, false);

        Bundle args = getArguments();
        if (args != null && args.containsKey(ARG_AMOUNT)) {
            binding.etAmount.setText(Money.plain(args.getDouble(ARG_AMOUNT)));
        }
        if (args != null && args.getString(ARG_NOTE) != null) {
            binding.etNote.setText(args.getString(ARG_NOTE));
        }

        accountViewModel.getAllAccounts().observe(getViewLifecycleOwner(), list -> {
            accounts = list;
            List<String> virtualNames = new ArrayList<>();
            List<String> realNames = new ArrayList<>();
            for (Account account : list) {
                (account.isReal() ? realNames : virtualNames).add(account.getName());
            }
            Ui.setDropdownItems(binding.etAccount, virtualNames);
            Ui.setDropdownItems(binding.etFrom, virtualNames);
            Ui.setDropdownItems(binding.etTo, virtualNames);
            Ui.setDropdownItems(binding.etRealAccount, realNames);
            Ui.setDropdownItems(binding.etFromReal, realNames);
            Ui.setDropdownItems(binding.etToReal, realNames);
            if (!isEditing() && binding.etAccount.getText().length() == 0) {
                for (Account account : list) {
                    if (account.isDefault() && account.isReal()) {
                        binding.etRealAccount.setText(account.getName(), false);
                        binding.etFromReal.setText(account.getName(), false);
                        binding.etToReal.setText(account.getName(), false);
                    } else if (account.isDefault()) {
                        binding.etAccount.setText(account.getName(), false);
                        binding.etFrom.setText(account.getName(), false);
                    }
                }
            }
            fillAccountsForEdit();
        });
        categoryViewModel.getAllCategories().observe(getViewLifecycleOwner(), list -> {
            categories = list;
            buildCategoryChips();
        });
        billsViewModel.getRecurringPayments().observe(getViewLifecycleOwner(), list -> {
            bills = list;
            List<String> names = new ArrayList<>();
            names.add(NO_BILL);
            for (RecurringPayment bill : list) {
                names.add(bill.getName());
            }
            Ui.setDropdownItems(binding.etBill, names);
            fillBillForEdit();
        });

        if (isEditing()) {
            binding.btnTransfer.setEnabled(false);
            binding.chipMore.performClick();
            Ui.observeOnce(viewModel.getTransaction(args.getLong(ARG_EDIT_ID)), getViewLifecycleOwner(), this::loadForEdit);
        }
        if (!isEditing() && binding.etAmount.getText().length() == 0) {
            binding.etAmount.requestFocus();
        }
        onTypeChanged();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (binding.etAmount.hasFocus() && getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                    | android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
    }

    private boolean isEditing() {
        return getArguments() != null && getArguments().containsKey(ARG_EDIT_ID);
    }

    private void loadForEdit(Transaction transaction) {
        if (transaction == null) {
            dismiss();
            return;
        }
        editing = transaction;
        binding.etAmount.setText(Money.plain(transaction.getAmount()));
        date.setTime(transaction.getDate());
        binding.chipDate.setText(dateFormat.format(date.getTime()));
        binding.etNote.setText(transaction.getDescription());
        binding.etReceiver.setText(transaction.getReceiverName());
        if (transaction.isTransfer()) {
            binding.btnTransfer.setEnabled(true);
            binding.btnExpense.setEnabled(false);
            binding.btnIncome.setEnabled(false);
            binding.typeToggle.check(R.id.btnTransfer);
            binding.tilBill.setVisibility(View.GONE);
            Ui.observeOnce(viewModel.getTransferLegs(transaction.getTransferGroupId()), getViewLifecycleOwner(), legs -> {
                for (Transaction leg : legs) {
                    if ("DEBIT".equals(leg.getTransactionType())) {
                        editingDebit = leg;
                    } else {
                        editingCredit = leg;
                    }
                }
                fillAccountsForEdit();
            });
        } else {
            binding.typeToggle.check("CREDIT".equals(transaction.getTransactionType())
                    ? R.id.btnIncome : R.id.btnExpense);
            if (!transaction.isNeedsReview()) {
                selectedCategory = transaction.getCategory();
            }
            buildCategoryChips();
            fillAccountsForEdit();
            fillBillForEdit();
        }
    }

    private void fillAccountsForEdit() {
        if (editing == null) {
            return;
        }
        if (editing.isTransfer()) {
            if (editingDebit != null && editingCredit != null) {
                binding.etFrom.setText(nameOf(editingDebit.getAccountId()), false);
                binding.etTo.setText(nameOf(editingCredit.getAccountId()), false);
                binding.etFromReal.setText(nameOf(editingDebit.getRealAccountId()), false);
                binding.etToReal.setText(nameOf(editingCredit.getRealAccountId()), false);
            }
        } else {
            binding.etAccount.setText(nameOf(editing.getAccountId()), false);
            binding.etRealAccount.setText(nameOf(editing.getRealAccountId()), false);
        }
    }

    private void fillBillForEdit() {
        if (editing == null || editing.getLinkedRecurringPaymentId() == null) {
            return;
        }
        for (RecurringPayment bill : bills) {
            if (bill.getId() == editing.getLinkedRecurringPaymentId()) {
                binding.etBill.setText(bill.getName(), false);
            }
        }
    }

    private String nameOf(Long accountId) {
        for (Account account : accounts) {
            if (accountId != null && account.getId() == accountId) {
                return account.getName();
            }
        }
        return "";
    }

    // A virtual and a real account can share a name, so the field says which kind it holds
    private Long accountIdOf(AutoCompleteTextView view, boolean real) {
        String name = view.getText().toString();
        for (Account account : accounts) {
            if (account.isReal() == real && account.getName().equals(name)) {
                return account.getId();
            }
        }
        return null;
    }

    private boolean isTransferMode() {
        return binding.typeToggle.getCheckedButtonId() == R.id.btnTransfer;
    }

    private boolean isIncomeMode() {
        return binding.typeToggle.getCheckedButtonId() == R.id.btnIncome;
    }

    private void onTypeChanged() {
        boolean transfer = isTransferMode();
        binding.groupCategory.setVisibility(transfer ? View.GONE : View.VISIBLE);
        binding.tilAccount.setVisibility(transfer ? View.GONE : View.VISIBLE);
        binding.tilRealAccount.setVisibility(transfer ? View.GONE : View.VISIBLE);
        binding.groupTransfer.setVisibility(transfer ? View.VISIBLE : View.GONE);
        binding.groupTransferReal.setVisibility(transfer ? View.VISIBLE : View.GONE);
        binding.tilReceiver.setVisibility(transfer ? View.GONE : View.VISIBLE);
        binding.tilReceiver.setHint(isIncomeMode() ? "Received from" : "Paid to");
        selectedCategoryStillValid();
        buildCategoryChips();
        updateSaveLabel();
    }

    private void selectedCategoryStillValid() {
        if (editing != null && !editing.isNeedsReview() && selectedCategory != null
                && selectedCategory.equals(editing.getCategory())) {
            return;
        }
        String type = isIncomeMode() ? "INCOME" : "EXPENSE";
        for (Category category : categories) {
            if (category.getName().equals(selectedCategory) && category.getType().equals(type)) {
                return;
            }
        }
        selectedCategory = null;
    }

    private void buildCategoryChips() {
        binding.chipsCategory.removeAllViews();
        String type = isIncomeMode() ? "INCOME" : "EXPENSE";
        Set<String> names = new LinkedHashSet<>();
        for (Category category : categories) {
            if (category.getType().equals(type)) {
                names.add(category.getName());
            }
        }
        // A row saved under a category of the other type keeps it until someone changes it
        if (selectedCategory != null) {
            names.add(selectedCategory);
        }
        for (String name : names) {
            Chip chip = new Chip(requireContext(), null, com.google.android.material.R.attr.chipStyle);
            chip.setText(name);
            chip.setCheckable(true);
            chip.setCheckedIconVisible(false);
            chip.setChecked(name.equals(selectedCategory));
            chip.setOnClickListener(v -> {
                selectedCategory = chip.isChecked() ? name : null;
                binding.tvCategoryLabel.setText("Category");
                binding.tvCategoryLabel.setTextColor(requireContext().getColor(R.color.on_surface_variant));
            });
            binding.chipsCategory.addView(chip);
        }
    }

    private void updateSaveLabel() {
        Double amount = Money.parse(binding.etAmount.getText().toString());
        if (isTransferMode() && !isEditing() && amount != null) {
            binding.btnSave.setText("Move " + Money.format(amount));
        } else {
            binding.btnSave.setText("Save");
        }
    }

    private void save() {
        Double amount = Money.parse(binding.etAmount.getText().toString());
        if (amount == null) {
            binding.tvAmountError.setVisibility(View.VISIBLE);
            return;
        }
        if (isTransferMode()) {
            saveTransfer(amount);
        } else {
            saveTransaction(amount);
        }
    }

    private void saveTransfer(double amount) {
        Long fromId = accountIdOf(binding.etFrom, false);
        Long toId = accountIdOf(binding.etTo, false);
        Long fromRealId = accountIdOf(binding.etFromReal, true);
        Long toRealId = accountIdOf(binding.etToReal, true);
        // Money can move on either side, but it has to move on at least one
        boolean nothingMoves = fromId != null && fromId.equals(toId) && fromRealId != null && fromRealId.equals(toRealId);
        binding.tilFrom.setError(fromId == null ? "Choose" : null);
        binding.tilTo.setError(toId == null ? "Choose" : nothingMoves ? "Pick another" : null);
        binding.tilFromReal.setError(fromRealId == null ? "Choose" : null);
        binding.tilToReal.setError(toRealId == null ? "Choose" : null);
        if (fromId == null || toId == null || fromRealId == null || toRealId == null || nothingMoves) {
            return;
        }
        String note = binding.etNote.getText().toString().trim();
        if (editing != null) {
            if (editingDebit == null || editingCredit == null) {
                return;
            }
            viewModel.updateTransfer(updatedLeg(editingDebit, amount, fromId, fromRealId, note),
                    updatedLeg(editingCredit, amount, toId, toRealId, note));
        } else {
            Transaction[] legs = Transfers.newPair(amount, fromId, toId, date.getTime(), note);
            legs[0].setRealAccountId(fromRealId);
            legs[1].setRealAccountId(toRealId);
            viewModel.insertTransfer(legs[0], legs[1]);
        }
        dismiss();
    }

    private Transaction updatedLeg(Transaction leg, double amount, long accountId, long realAccountId, String note) {
        Transaction copy = leg.copy();
        copy.setAmount(amount);
        copy.setAccountId(accountId);
        copy.setRealAccountId(realAccountId);
        copy.setDescription(note);
        copy.setDate(date.getTime());
        return copy;
    }

    private void saveTransaction(double amount) {
        Long accountId = accountIdOf(binding.etAccount, false);
        Long realAccountId = accountIdOf(binding.etRealAccount, true);
        binding.tilAccount.setError(accountId == null ? "Choose a virtual account" : null);
        binding.tilRealAccount.setError(realAccountId == null ? "Choose a real account" : null);
        if (accountId == null || realAccountId == null) {
            return;
        }
        if (selectedCategory == null) {
            binding.tvCategoryLabel.setText("Choose a category");
            binding.tvCategoryLabel.setTextColor(requireContext().getColor(R.color.alert));
            return;
        }

        Transaction row = editing != null ? editing.copy() : new Transaction();
        row.setAmount(amount);
        row.setTransactionType(isIncomeMode() ? "CREDIT" : "DEBIT");
        row.setDate(date.getTime());
        row.setCategory(selectedCategory);
        row.setAccountId(accountId);
        row.setRealAccountId(realAccountId);
        row.setDescription(binding.etNote.getText().toString().trim());
        row.setReceiverName(binding.etReceiver.getText().toString().trim());
        row.setNeedsReview(false);

        RecurringPayment bill = selectedBill();
        Long previousBill = row.getLinkedRecurringPaymentId();
        row.setLinkedRecurringPaymentId(bill != null ? bill.getId() : null);
        if (bill != null && (previousBill == null || previousBill != bill.getId())) {
            bill.setCompleted(true);
            bill.setLastCompletedDate(date.getTime());
            billsViewModel.update(bill);
        }

        if (editing != null) {
            viewModel.updateTransaction(editing, row);
        } else {
            Bundle args = getArguments();
            if (args != null && args.getString(ARG_MESSAGE) != null) {
                String message = args.getString(ARG_MESSAGE);
                row.setSmsBody(message);
                row.setSmsSender(args.getString(ARG_SENDER));
                row.setDedupKey(TransactionKeys.bodyKey(message));
                row.setMessageTime(date.getTimeInMillis());
                row.setSource(TransactionKeys.SOURCE_PASTE);
            }
            viewModel.insertTransaction(row);
        }
        dismiss();
    }

    private RecurringPayment selectedBill() {
        String name = binding.etBill.getText().toString();
        for (RecurringPayment bill : bills) {
            if (bill.getName().equals(name)) {
                return bill;
            }
        }
        return null;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
