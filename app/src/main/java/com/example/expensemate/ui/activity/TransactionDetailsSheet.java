package com.example.expensemate.ui.activity;

import android.os.Bundle;
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
import com.example.expensemate.databinding.SheetTransactionDetailsBinding;
import com.example.expensemate.ui.common.ExpandedSheet;
import com.example.expensemate.ui.common.TransactionText;
import com.example.expensemate.ui.common.Ui;
import com.example.expensemate.util.Money;
import com.example.expensemate.viewmodel.AccountViewModel;
import com.example.expensemate.viewmodel.CategoryViewModel;
import com.example.expensemate.viewmodel.RecurringPaymentsViewModel;
import com.example.expensemate.viewmodel.TransactionViewModel;
import com.google.android.material.chip.Chip;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Everything about one transaction, and every action on it, in one place. */
public class TransactionDetailsSheet extends ExpandedSheet {
    private static final String ARG_ID = "id";

    private SheetTransactionDetailsBinding binding;
    private TransactionViewModel viewModel;
    private Transaction transaction;
    private List<Transaction> legs = new ArrayList<>();
    private List<Category> categories = new ArrayList<>();
    private List<RecurringPayment> bills = new ArrayList<>();
    private Map<Long, String> accountNames = new HashMap<>();
    private List<Account> accounts = new ArrayList<>();
    private final SimpleDateFormat whenFormat = new SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault());

    public static void show(FragmentActivity activity, long transactionId) {
        Bundle args = new Bundle();
        args.putLong(ARG_ID, transactionId);
        TransactionDetailsSheet sheet = new TransactionDetailsSheet();
        sheet.setArguments(args);
        sheet.show(activity.getSupportFragmentManager(), "transaction_details");
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = SheetTransactionDetailsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        ViewModelProvider provider = new ViewModelProvider(requireActivity());
        viewModel = provider.get(TransactionViewModel.class);

        binding.btnEdit.setOnClickListener(v -> {
            FragmentActivity activity = requireActivity();
            long id = transaction.getId();
            dismiss();
            TransactionFormSheet.showEdit(activity, id);
        });
        binding.btnDelete.setOnClickListener(v -> {
            FragmentActivity activity = requireActivity();
            Transaction toDelete = transaction;
            dismiss();
            Ui.deleteWithUndo(activity, viewModel, toDelete);
        });
        binding.switchCount.setOnClickListener(v -> viewModel.setExcludedFromSummary(transaction.getId(),
                !binding.switchCount.isChecked()));

        provider.get(AccountViewModel.class).getAllAccounts().observe(getViewLifecycleOwner(), accounts -> {
            Map<Long, String> names = new HashMap<>();
            for (Account account : accounts) {
                names.put(account.getId(), account.getName());
            }
            accountNames = names;
            this.accounts = accounts;
            render();
        });
        provider.get(CategoryViewModel.class).getAllCategories().observe(getViewLifecycleOwner(), list -> {
            categories = list;
            render();
        });
        provider.get(RecurringPaymentsViewModel.class).getRecurringPayments().observe(getViewLifecycleOwner(), list -> {
            bills = list;
            render();
        });
        viewModel.getTransaction(requireArguments().getLong(ARG_ID)).observe(getViewLifecycleOwner(), row -> {
            if (row == null) {
                dismissAllowingStateLoss();
                return;
            }
            boolean firstLoad = transaction == null;
            transaction = row;
            if (firstLoad && row.isTransfer()) {
                viewModel.getTransferLegs(row.getTransferGroupId()).observe(getViewLifecycleOwner(), list -> {
                    legs = list;
                    render();
                });
            }
            render();
        });
    }

    private void render() {
        if (transaction == null || binding == null) {
            return;
        }
        binding.tvWhen.setText(whenFormat.format(transaction.getDate()));
        binding.details.removeAllViews();
        if (transaction.isTransfer()) {
            renderTransfer();
        } else {
            renderTransaction();
        }
    }

    private void renderTransfer() {
        Ui.styleIconTile(binding.ivIcon, R.drawable.ic_swap, R.color.transfer_container, R.color.transfer);
        String from = "Unknown";
        String to = "Unknown";
        for (Transaction leg : legs) {
            String name = TransactionText.accountName(accountNames, leg.getAccountId());
            if ("DEBIT".equals(leg.getTransactionType())) {
                from = name;
            } else {
                to = name;
            }
        }
        binding.tvTitle.setText(from + " → " + to);
        binding.tvAmount.setText(Money.format(transaction.getAmount()));
        binding.tvAmount.setTextColor(requireContext().getColor(R.color.transfer));
        binding.groupCategory.setVisibility(View.GONE);
        binding.rowCount.setVisibility(View.GONE);
        binding.tvMessage.setVisibility(View.GONE);
        Ui.addKeyValue(binding.details, "From", from);
        Ui.addKeyValue(binding.details, "To", to);
        if (TransactionText.hasText(transaction.getDescription())) {
            Ui.addKeyValue(binding.details, "Note", transaction.getDescription());
        }
        Ui.addKeyValue(binding.details, "In your totals", "No, it only moves money between accounts");
    }

    private void renderTransaction() {
        boolean credit = "CREDIT".equals(transaction.getTransactionType());
        Ui.styleIconTile(binding.ivIcon, TransactionText.categoryIcon(transaction.getCategory()),
                credit ? R.color.primary_container : R.color.surface_variant,
                credit ? R.color.primary : R.color.on_surface);
        binding.tvTitle.setText(TransactionText.title(transaction));
        binding.tvAmount.setText(credit ? Money.credit(transaction.getAmount()) : Money.debit(transaction.getAmount()));
        binding.tvAmount.setTextColor(requireContext().getColor(credit ? R.color.credit : R.color.on_surface));

        binding.groupCategory.setVisibility(View.VISIBLE);
        buildCategoryChips(credit);
        buildAccountChips();

        if (TransactionText.hasText(transaction.getReceiverName())) {
            Ui.addKeyValue(binding.details, credit ? "Received from" : "Paid to", transaction.getReceiverName());
        }
        Ui.addKeyValue(binding.details, "Linked bill", linkedBillName());

        binding.rowCount.setVisibility(View.VISIBLE);
        binding.switchCount.setChecked(!transaction.isExcludedFromSummary());

        String message = transaction.getSmsBody();
        boolean hasMessage = TransactionText.hasText(message);
        binding.tvMessage.setVisibility(hasMessage ? View.VISIBLE : View.GONE);
        if (hasMessage) {
            binding.tvMessage.setText("From your message: " + message);
        } else if (TransactionText.hasText(transaction.getDescription())) {
            Ui.addKeyValue(binding.details, "Note", transaction.getDescription());
        }
    }

    private String linkedBillName() {
        Long billId = transaction.getLinkedRecurringPaymentId();
        if (billId != null) {
            for (RecurringPayment bill : bills) {
                if (bill.getId() == billId) {
                    return bill.getName();
                }
            }
        }
        return "None";
    }

    private void buildCategoryChips(boolean credit) {
        binding.chipsCategory.removeAllViews();
        String type = credit ? "INCOME" : "EXPENSE";
        Set<String> names = new LinkedHashSet<>();
        // A row waiting for review shows its default first, unselected until someone confirms it
        names.add(transaction.getCategory());
        for (Category category : categories) {
            if (category.getType().equals(type)) {
                names.add(category.getName());
            }
        }
        for (String name : names) {
            Chip chip = new Chip(requireContext(), null, com.google.android.material.R.attr.chipStyle);
            chip.setText(name);
            chip.setCheckable(true);
            chip.setCheckedIconVisible(false);
            chip.setChecked(!transaction.isNeedsReview() && name.equals(transaction.getCategory()));
            chip.setOnClickListener(v -> viewModel.setCategory(transaction, name));
            binding.chipsCategory.addView(chip);
        }
    }

    private void buildAccountChips() {
        binding.chipsAccount.removeAllViews();
        Long selected = transaction.getAccountId();
        // The current account comes first, so it is in view without scrolling
        for (Account account : accounts) {
            if (selected != null && account.getId() == selected) {
                addAccountChip(account, true);
            }
        }
        for (Account account : accounts) {
            if (selected == null || account.getId() != selected) {
                addAccountChip(account, false);
            }
        }
    }

    private void addAccountChip(Account account, boolean checked) {
        Chip chip = new Chip(requireContext(), null, com.google.android.material.R.attr.chipStyle);
        chip.setText(account.getName());
        chip.setCheckable(true);
        chip.setCheckedIconVisible(false);
        chip.setChecked(checked);
        chip.setOnClickListener(v -> viewModel.setAccount(transaction.getId(), account.getId()));
        binding.chipsAccount.addView(chip);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
