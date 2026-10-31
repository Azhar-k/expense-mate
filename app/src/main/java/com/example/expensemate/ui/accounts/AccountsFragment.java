package com.example.expensemate.ui.accounts;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.example.expensemate.R;
import com.example.expensemate.data.Account;
import com.example.expensemate.data.AccountBalance;
import com.example.expensemate.databinding.FragmentAccountsBinding;
import com.example.expensemate.databinding.ItemAccountBinding;
import com.example.expensemate.ui.common.Ui;
import com.example.expensemate.util.Money;
import com.example.expensemate.viewmodel.AccountViewModel;
import com.example.expensemate.viewmodel.TransactionViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AccountsFragment extends Fragment {
    private FragmentAccountsBinding binding;
    private AccountViewModel accountViewModel;
    private List<Account> accounts = new ArrayList<>();
    private Map<Long, Double> balances = new HashMap<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentAccountsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        ViewModelProvider provider = new ViewModelProvider(requireActivity());
        accountViewModel = provider.get(AccountViewModel.class);
        TransactionViewModel transactionViewModel = provider.get(TransactionViewModel.class);

        binding.fabAdd.setOnClickListener(v -> AccountForm.show(requireActivity(), null));
        binding.btnInfo.setOnClickListener(v -> new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Net balance")
                .setMessage("Total income minus total expenses across all your accounts. "
                        + "Transactions you've left out of the summary don't count, and transfers between "
                        + "your own accounts cancel out.")
                .setPositiveButton("OK", null)
                .show());

        accountViewModel.getAllAccounts().observe(getViewLifecycleOwner(), list -> {
            accounts = list;
            render();
        });
        transactionViewModel.getAccountBalances().observe(getViewLifecycleOwner(), list -> {
            Map<Long, Double> updated = new HashMap<>();
            for (AccountBalance balance : list) {
                updated.put(balance.accountId, balance.balance);
            }
            balances = updated;
            render();
        });
        transactionViewModel.getNetBalance().observe(getViewLifecycleOwner(), balance -> {
            double value = balance != null ? balance : 0;
            binding.tvNetBalance.setText(Money.format(value));
            binding.tvNetBalance.setTextColor(requireContext().getColor(value < 0 ? R.color.alert : R.color.on_surface));
        });
    }

    private void render() {
        binding.listAccounts.removeAllViews();
        for (int i = 0; i < accounts.size(); i++) {
            binding.listAccounts.addView(buildRow(accounts.get(i), i == 0, i == accounts.size() - 1));
        }
    }

    private View buildRow(Account account, boolean first, boolean last) {
        ItemAccountBinding row = ItemAccountBinding.inflate(getLayoutInflater(), binding.listAccounts, false);
        row.getRoot().setBackgroundResource(first && last ? R.drawable.bg_row_single
                : first ? R.drawable.bg_row_top : last ? R.drawable.bg_row_bottom : R.drawable.bg_row_middle);
        row.divider.setVisibility(last ? View.GONE : View.VISIBLE);

        row.tvName.setText(account.getName());
        row.tvInitials.setText(initials(account.getName()));
        String detail = AccountForm.detailLine(account);
        row.tvDetail.setText(detail);
        row.tvDetail.setVisibility(detail.isEmpty() ? View.GONE : View.VISIBLE);
        if (account.isDefault()) {
            Ui.styleTag(row.tvDefault, "Default", R.color.primary_container, R.color.on_primary_container);
        }

        Double balance = balances.get(account.getId());
        double value = balance != null ? balance : 0;
        row.tvBalance.setText(Money.format(value));
        row.tvBalance.setTextColor(requireContext().getColor(value < 0 ? R.color.alert : R.color.on_surface));

        row.getRoot().setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putLong("accountId", account.getId());
            NavHostFragment.findNavController(this).navigate(R.id.account_details_fragment, args);
        });
        row.getRoot().setOnLongClickListener(v -> {
            showMenu(v, account);
            return true;
        });
        return row.getRoot();
    }

    private void showMenu(View anchor, Account account) {
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        menu.getMenu().add(Menu.EDIT, Menu.EDIT, 0, "Edit");
        if (!account.isDefault()) {
            menu.getMenu().add(Menu.SET_DEFAULT, Menu.SET_DEFAULT, 1, "Set as default");
        }
        menu.getMenu().add(Menu.DELETE, Menu.DELETE, 2, "Delete");
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == Menu.EDIT) {
                AccountForm.show(requireActivity(), account);
            } else if (item.getItemId() == Menu.SET_DEFAULT) {
                accountViewModel.setDefaultAccount(account.getId());
            } else {
                AccountForm.confirmDelete(requireActivity(), account, null);
            }
            return true;
        });
        menu.show();
    }

    private static String initials(String name) {
        String[] words = name.trim().split("\\s+");
        String letters = words.length > 1
                ? words[0].substring(0, 1) + words[1].substring(0, 1)
                : name.trim().substring(0, Math.min(2, name.trim().length()));
        return letters.toUpperCase(Locale.getDefault());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private static final class Menu {
        static final int EDIT = 1;
        static final int SET_DEFAULT = 2;
        static final int DELETE = 3;
    }
}
