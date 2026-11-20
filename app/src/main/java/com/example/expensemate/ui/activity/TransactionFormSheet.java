package com.example.expensemate.ui.activity;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.expensemate.R;
import com.example.expensemate.data.Account;
import com.example.expensemate.data.Category;
import com.example.expensemate.data.Fund;
import com.example.expensemate.data.RecurringPayment;
import com.example.expensemate.data.Transaction;
import com.example.expensemate.data.TransactionKeys;
import com.example.expensemate.data.Transfers;
import com.example.expensemate.databinding.SheetTransactionFormBinding;
import com.example.expensemate.ui.common.ExpandedSheet;
import com.example.expensemate.ui.common.Pickers;
import com.example.expensemate.ui.common.Ui;
import com.example.expensemate.util.Currencies;
import com.example.expensemate.util.Money;
import com.example.expensemate.viewmodel.AccountViewModel;
import com.example.expensemate.viewmodel.CategoryViewModel;
import com.example.expensemate.viewmodel.FundViewModel;
import com.example.expensemate.viewmodel.RecurringPaymentsViewModel;
import com.example.expensemate.viewmodel.TransactionViewModel;
import com.google.android.material.chip.Chip;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    // What the money is for, and where it is; transfers have both on each side
    private AccountChips fund;
    private AccountChips account;
    private AccountChips fromFund;
    private AccountChips toFund;
    private AccountChips fromAccount;
    private AccountChips toAccount;
    private List<Category> categories = new ArrayList<>();
    private List<RecurringPayment> bills = new ArrayList<>();
    private String selectedCategory;
    // Guessed from past transactions for a row still waiting for review; shown picked, saved only if kept
    private String suggestedCategory;
    private Long suggestedBillId;
    private Transaction editing;
    private Transaction editingDebit;
    private Transaction editingCredit;
    private final Map<Long, Account> accountsById = new HashMap<>();
    private String mainCurrency = Currencies.fromLocale();

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
        FundViewModel fundViewModel = provider.get(FundViewModel.class);
        CategoryViewModel categoryViewModel = provider.get(CategoryViewModel.class);

        fund = new AccountChips(binding.pickFund, "Fund");
        account = new AccountChips(binding.pickAccount, "Account");
        fromFund = new AccountChips(binding.pickFromFund, "From fund");
        toFund = new AccountChips(binding.pickToFund, "To fund");
        fromAccount = new AccountChips(binding.pickFromAccount, "From account");
        toAccount = new AccountChips(binding.pickToAccount, "To account");
        account.setOnChange(this::onCurrencyChanged);
        fromAccount.setOnChange(this::onCurrencyChanged);
        toAccount.setOnChange(this::onCurrencyChanged);
        binding.etReceived.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                binding.tilReceived.setError(null);
                updateSaveLabel();
            }
        });
        binding.pickCategory.tvLabel.setText("Category");
        // A category can be left empty, so tapping the chosen one clears it
        binding.pickCategory.chips.setSelectionRequired(false);

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

        fundViewModel.getAllFunds().observe(getViewLifecycleOwner(), list -> {
            boolean firstFill = fund.selectedId() == null;
            fund.setFunds(list);
            fromFund.setFunds(list);
            toFund.setFunds(list);
            if (!isEditing() && firstFill) {
                for (Fund each : list) {
                    if (each.isDefault()) {
                        fund.select(each.getId());
                        fromFund.select(each.getId());
                    }
                }
            }
            fillAccountsForEdit();
        });
        accountViewModel.getAllAccounts().observe(getViewLifecycleOwner(), list -> {
            for (Account each : list) {
                accountsById.put(each.getId(), each);
            }
            mainCurrency = Currencies.main(list);
            boolean firstFill = account.selectedId() == null;
            account.setAccounts(list);
            fromAccount.setAccounts(list);
            toAccount.setAccounts(list);
            if (!isEditing() && firstFill) {
                for (Account each : list) {
                    if (each.isDefault()) {
                        account.select(each.getId());
                        fromAccount.select(each.getId());
                        toAccount.select(each.getId());
                    }
                }
            }
            fillAccountsForEdit();
            onCurrencyChanged();
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
                // The amount is what left; the row tapped may be the side that arrived, in another currency
                if (editingDebit != null) {
                    binding.etAmount.setText(Money.plain(editingDebit.getAmount()));
                }
                if (editingCredit != null && editingDebit != null
                        && !editingCredit.getCurrency().equals(editingDebit.getCurrency())) {
                    binding.etReceived.setText(Money.plain(editingCredit.getAmount()));
                }
                fillAccountsForEdit();
                onCurrencyChanged();
            });
        } else {
            binding.typeToggle.check("CREDIT".equals(transaction.getTransactionType())
                    ? R.id.btnIncome : R.id.btnExpense);
            if (!transaction.isNeedsReview()) {
                selectedCategory = transaction.getCategory();
            } else {
                suggestedCategory = transaction.getCategory();
            }
            buildCategoryChips();
            fillAccountsForEdit();
            fillBillForEdit();
            if (transaction.isNeedsReview() && transaction.getLinkedRecurringPaymentId() == null) {
                viewModel.suggestBill(transaction, billId -> {
                    if (binding != null && editing == transaction) {
                        suggestedBillId = billId;
                        fillBillForEdit();
                    }
                });
            }
        }
    }

    private void fillAccountsForEdit() {
        if (editing == null) {
            return;
        }
        if (editing.isTransfer()) {
            if (editingDebit != null && editingCredit != null) {
                fromFund.select(editingDebit.getFundId());
                toFund.select(editingCredit.getFundId());
                fromAccount.select(editingDebit.getAccountId());
                toAccount.select(editingCredit.getAccountId());
            }
        } else {
            fund.select(editing.getFundId());
            account.select(editing.getAccountId());
        }
    }

    private void fillBillForEdit() {
        if (editing == null) {
            return;
        }
        Long billId = editing.getLinkedRecurringPaymentId();
        // A suggestion never replaces a bill someone already picked
        if (billId == null && selectedBill() == null) {
            billId = suggestedBillId;
        }
        if (billId == null) {
            return;
        }
        for (RecurringPayment bill : bills) {
            if (bill.getId() == billId) {
                binding.etBill.setText(bill.getName(), false);
            }
        }
    }

    private boolean isTransferMode() {
        return binding.typeToggle.getCheckedButtonId() == R.id.btnTransfer;
    }

    private boolean isIncomeMode() {
        return binding.typeToggle.getCheckedButtonId() == R.id.btnIncome;
    }

    private void onTypeChanged() {
        boolean transfer = isTransferMode();
        binding.pickCategory.getRoot().setVisibility(transfer ? View.GONE : View.VISIBLE);
        binding.pickFund.getRoot().setVisibility(transfer ? View.GONE : View.VISIBLE);
        binding.pickAccount.getRoot().setVisibility(transfer ? View.GONE : View.VISIBLE);
        binding.groupTransfer.setVisibility(transfer ? View.VISIBLE : View.GONE);
        binding.tilReceiver.setVisibility(transfer ? View.GONE : View.VISIBLE);
        binding.tilReceiver.setHint(isIncomeMode() ? "Received from" : "Paid to");
        selectedCategoryStillValid();
        buildCategoryChips();
        onCurrencyChanged();
    }

    /** What an account holds, or the main currency when none is picked or it has none. */
    private String currencyOf(Long accountId) {
        Account picked = accountId != null ? accountsById.get(accountId) : null;
        String currency = picked != null ? picked.getCurrency() : null;
        return currency != null ? currency : mainCurrency;
    }

    /** The amount is in the currency of the account it comes from. */
    private String amountCurrency() {
        return currencyOf(isTransferMode() ? fromAccount.selectedId() : account.selectedId());
    }

    /** A transfer between accounts in different currencies arrives as a different amount. */
    private boolean crossCurrency() {
        return isTransferMode() && !currencyOf(fromAccount.selectedId()).equals(currencyOf(toAccount.selectedId()));
    }

    private void onCurrencyChanged() {
        if (binding == null) {
            return;
        }
        binding.tvCurrency.setText(Money.symbol(amountCurrency()));
        boolean cross = crossCurrency();
        binding.tilReceived.setVisibility(cross ? View.VISIBLE : View.GONE);
        if (cross) {
            String to = currencyOf(toAccount.selectedId());
            binding.tilReceived.setHint("Amount received, in " + to);
            binding.tilReceived.setPrefixText(Money.symbol(to));
        }
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
        binding.pickCategory.chips.removeAllViews();
        String type = isIncomeMode() ? "INCOME" : "EXPENSE";
        // Waits for the list so the default category, which nobody guessed, is never shown as picked
        if (suggestedCategory != null && selectedCategory == null && !categories.isEmpty()) {
            for (Category category : categories) {
                if (category.getName().equals(suggestedCategory) && category.getType().equals(type)
                        && !category.isDefault()) {
                    selectedCategory = suggestedCategory;
                }
            }
            suggestedCategory = null;
        }
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
            Chip chip = new Chip(new ContextThemeWrapper(requireContext(), R.style.ThemeOverlay_ExpenseMate_FormChips),
                    null, com.google.android.material.R.attr.chipStyle);
            chip.setText(name);
            chip.setCheckable(true);
            chip.setCheckedIconVisible(false);
            chip.setChecked(name.equals(selectedCategory));
            chip.setOnClickListener(v -> {
                selectedCategory = chip.isChecked() ? name : null;
                binding.pickCategory.tvLabel.setText("Category");
                binding.pickCategory.tvLabel.setTextColor(requireContext().getColor(R.color.on_surface_variant));
            });
            binding.pickCategory.chips.addView(chip);
            if (chip.isChecked()) {
                binding.pickCategory.scroll.post(() -> binding.pickCategory.scroll.smoothScrollTo(
                        Math.max(0, chip.getLeft() - binding.pickCategory.chips.getPaddingStart()), 0));
            }
        }
    }

    private void updateSaveLabel() {
        Double amount = Money.parse(binding.etAmount.getText().toString());
        if (isTransferMode() && !isEditing() && amount != null) {
            binding.btnSave.setText("Move " + Money.format(amount, amountCurrency()));
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
        Long fromFundId = fromFund.selectedId();
        Long toFundId = toFund.selectedId();
        Long fromAccountId = fromAccount.selectedId();
        Long toAccountId = toAccount.selectedId();
        // Money can move between funds, between accounts or both, but it has to move somewhere
        boolean nothingMoves = fromFundId != null && fromFundId.equals(toFundId)
                && fromAccountId != null && fromAccountId.equals(toAccountId);
        fromFund.setError(fromFundId == null ? "Choose where it comes from" : null);
        toFund.setError(toFundId == null ? "Choose where it goes"
                : nothingMoves ? "Pick another, so money moves on one side" : null);
        fromAccount.setError(fromAccountId == null ? "Choose where it comes from" : null);
        toAccount.setError(toAccountId == null ? "Choose where it goes" : null);
        if (fromFundId == null || toFundId == null || fromAccountId == null || toAccountId == null || nothingMoves) {
            return;
        }
        double received = amount;
        if (crossCurrency()) {
            Double typed = Money.parse(binding.etReceived.getText() == null ? null : binding.etReceived.getText().toString());
            binding.tilReceived.setError(typed == null ? "Enter what arrived" : null);
            if (typed == null) {
                return;
            }
            received = typed;
        }
        String note = binding.etNote.getText().toString().trim();
        if (editing != null) {
            if (editingDebit == null || editingCredit == null) {
                return;
            }
            viewModel.updateTransfer(updatedLeg(editingDebit, amount, fromFundId, fromAccountId, note),
                    updatedLeg(editingCredit, received, toFundId, toAccountId, note));
        } else {
            Transaction[] legs = Transfers.newPair(amount, fromFundId, toFundId, date.getTime(), note);
            legs[0].setAccountId(fromAccountId);
            legs[1].setAccountId(toAccountId);
            legs[1].setAmount(received);
            viewModel.insertTransfer(legs[0], legs[1]);
        }
        dismiss();
    }

    private Transaction updatedLeg(Transaction leg, double amount, long fundId, long accountId, String note) {
        Transaction copy = leg.copy();
        copy.setAmount(amount);
        copy.setFundId(fundId);
        copy.setAccountId(accountId);
        copy.setDescription(note);
        copy.setDate(date.getTime());
        return copy;
    }

    private void saveTransaction(double amount) {
        Long fundId = fund.selectedId();
        Long accountId = account.selectedId();
        fund.setError(fundId == null ? "Choose a fund" : null);
        account.setError(accountId == null ? "Choose an account" : null);
        if (fundId == null || accountId == null) {
            return;
        }
        if (selectedCategory == null) {
            binding.pickCategory.tvLabel.setText("Choose a category");
            binding.pickCategory.tvLabel.setTextColor(requireContext().getColor(R.color.alert));
            return;
        }

        Transaction row = editing != null ? editing.copy() : new Transaction();
        row.setAmount(amount);
        row.setTransactionType(isIncomeMode() ? "CREDIT" : "DEBIT");
        row.setDate(date.getTime());
        row.setCategory(selectedCategory);
        row.setFundId(fundId);
        row.setAccountId(accountId);
        row.setDescription(binding.etNote.getText().toString().trim());
        row.setReceiverName(binding.etReceiver.getText().toString().trim());
        row.setNeedsReview(false);

        RecurringPayment bill = selectedBill();
        row.setLinkedRecurringPaymentId(bill != null ? bill.getId() : null);

        if (editing != null) {
            viewModel.updateTransaction(row);
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
