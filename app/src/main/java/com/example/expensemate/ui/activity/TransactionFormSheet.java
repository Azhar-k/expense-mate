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
    private AccountChips account;
    private AccountChips realAccount;
    private AccountChips from;
    private AccountChips to;
    private AccountChips fromReal;
    private AccountChips toReal;
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
        CategoryViewModel categoryViewModel = provider.get(CategoryViewModel.class);

        account = new AccountChips(binding.pickAccount, "Virtual account");
        realAccount = new AccountChips(binding.pickRealAccount, "Real account");
        from = new AccountChips(binding.pickFrom, "From virtual");
        to = new AccountChips(binding.pickTo, "To virtual");
        fromReal = new AccountChips(binding.pickFromReal, "From real");
        toReal = new AccountChips(binding.pickToReal, "To real");
        realAccount.setOnChange(this::onCurrencyChanged);
        fromReal.setOnChange(this::onCurrencyChanged);
        toReal.setOnChange(this::onCurrencyChanged);
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

        accountViewModel.getAllAccounts().observe(getViewLifecycleOwner(), list -> {
            List<Account> virtual = new ArrayList<>();
            List<Account> real = new ArrayList<>();
            for (Account each : list) {
                accountsById.put(each.getId(), each);
                (each.isReal() ? real : virtual).add(each);
            }
            mainCurrency = Currencies.main(list);
            boolean firstFill = account.selectedId() == null && realAccount.selectedId() == null;
            account.setAccounts(virtual);
            from.setAccounts(virtual);
            to.setAccounts(virtual);
            realAccount.setAccounts(real);
            fromReal.setAccounts(real);
            toReal.setAccounts(real);
            if (!isEditing() && firstFill) {
                for (Account each : list) {
                    if (each.isDefault() && each.isReal()) {
                        realAccount.select(each.getId());
                        fromReal.select(each.getId());
                        toReal.select(each.getId());
                    } else if (each.isDefault()) {
                        account.select(each.getId());
                        from.select(each.getId());
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
                from.select(editingDebit.getAccountId());
                to.select(editingCredit.getAccountId());
                fromReal.select(editingDebit.getRealAccountId());
                toReal.select(editingCredit.getRealAccountId());
            }
        } else {
            account.select(editing.getAccountId());
            realAccount.select(editing.getRealAccountId());
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
        binding.pickAccount.getRoot().setVisibility(transfer ? View.GONE : View.VISIBLE);
        binding.pickRealAccount.getRoot().setVisibility(transfer ? View.GONE : View.VISIBLE);
        binding.groupTransfer.setVisibility(transfer ? View.VISIBLE : View.GONE);
        binding.tilReceiver.setVisibility(transfer ? View.GONE : View.VISIBLE);
        binding.tilReceiver.setHint(isIncomeMode() ? "Received from" : "Paid to");
        selectedCategoryStillValid();
        buildCategoryChips();
        onCurrencyChanged();
    }

    /** What a real account holds, or the main currency when none is picked or it has none. */
    private String currencyOf(Long realAccountId) {
        Account real = realAccountId != null ? accountsById.get(realAccountId) : null;
        String currency = Currencies.of(real);
        return currency != null ? currency : mainCurrency;
    }

    /** The amount is in the currency of the real account it comes from. */
    private String amountCurrency() {
        return currencyOf(isTransferMode() ? fromReal.selectedId() : realAccount.selectedId());
    }

    /** A transfer between real accounts in different currencies arrives as a different amount. */
    private boolean crossCurrency() {
        return isTransferMode() && !currencyOf(fromReal.selectedId()).equals(currencyOf(toReal.selectedId()));
    }

    private void onCurrencyChanged() {
        if (binding == null) {
            return;
        }
        binding.tvCurrency.setText(Money.symbol(amountCurrency()));
        boolean cross = crossCurrency();
        binding.tilReceived.setVisibility(cross ? View.VISIBLE : View.GONE);
        if (cross) {
            String to = currencyOf(toReal.selectedId());
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
        Long fromId = from.selectedId();
        Long toId = to.selectedId();
        Long fromRealId = fromReal.selectedId();
        Long toRealId = toReal.selectedId();
        // Money can move on either side, but it has to move on at least one
        boolean nothingMoves = fromId != null && fromId.equals(toId) && fromRealId != null && fromRealId.equals(toRealId);
        from.setError(fromId == null ? "Choose where it comes from" : null);
        to.setError(toId == null ? "Choose where it goes"
                : nothingMoves ? "Pick another, so money moves on one side" : null);
        fromReal.setError(fromRealId == null ? "Choose where it comes from" : null);
        toReal.setError(toRealId == null ? "Choose where it goes" : null);
        if (fromId == null || toId == null || fromRealId == null || toRealId == null || nothingMoves) {
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
            viewModel.updateTransfer(updatedLeg(editingDebit, amount, fromId, fromRealId, note),
                    updatedLeg(editingCredit, received, toId, toRealId, note));
        } else {
            Transaction[] legs = Transfers.newPair(amount, fromId, toId, date.getTime(), note);
            legs[0].setRealAccountId(fromRealId);
            legs[1].setRealAccountId(toRealId);
            legs[1].setAmount(received);
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
        Long accountId = account.selectedId();
        Long realAccountId = realAccount.selectedId();
        account.setError(accountId == null ? "Choose a virtual account" : null);
        realAccount.setError(realAccountId == null ? "Choose a real account" : null);
        if (accountId == null || realAccountId == null) {
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
        row.setAccountId(accountId);
        row.setRealAccountId(realAccountId);
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
