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
import com.example.expensemate.data.Balance;
import com.example.expensemate.data.Fund;
import com.example.expensemate.databinding.FragmentMoneyBinding;
import com.example.expensemate.databinding.ItemAccountBinding;
import com.example.expensemate.ui.budgets.BudgetForm;
import com.example.expensemate.ui.budgets.BudgetsFragment;
import com.example.expensemate.ui.common.ReorderDialog;
import com.example.expensemate.ui.common.Ui;
import com.example.expensemate.util.Currencies;
import com.example.expensemate.util.CurrencyTotals;
import com.example.expensemate.viewmodel.AccountViewModel;
import com.example.expensemate.viewmodel.FundViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The Money tab: funds (what money is for), budgets (how everyday money is spent) and accounts
 * (where money is). Funds and accounts are two views of the same money, so their totals match.
 */
public class MoneyFragment extends Fragment {
    static final int FUNDS = 0;
    static final int BUDGETS = 1;
    static final int ACCOUNTS = 2;
    private static final String STATE_SEGMENT = "segment";

    private FragmentMoneyBinding binding;
    private AccountViewModel accountViewModel;
    private FundViewModel fundViewModel;
    private List<Fund> funds = new ArrayList<>();
    private List<Account> accounts = new ArrayList<>();
    // Fund and account ids can be equal, so each kind keeps its own balances
    private Map<Long, CurrencyTotals> fundBalances = new HashMap<>();
    private Map<Long, CurrencyTotals> accountBalances = new HashMap<>();
    private String mainCurrency = Currencies.fromLocale();
    private int segment = FUNDS; // Kept on the instance, so coming back from details keeps the segment

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentMoneyBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            segment = savedInstanceState.getInt(STATE_SEGMENT, segment);
        }
        ViewModelProvider provider = new ViewModelProvider(requireActivity());
        accountViewModel = provider.get(AccountViewModel.class);
        fundViewModel = provider.get(FundViewModel.class);

        // The child manager keeps Budgets across trips to details, so it is only added once
        if (getChildFragmentManager().findFragmentById(R.id.budgetsContainer) == null) {
            getChildFragmentManager().beginTransaction()
                    .replace(R.id.budgetsContainer, new BudgetsFragment())
                    .commit();
        }

        Ui.addReorderAction(this, this::reorder);
        binding.fabAdd.setOnClickListener(v -> add());
        binding.btnInfo.setOnClickListener(v -> explainTotal());

        binding.tabs.selectTab(binding.tabs.getTabAt(segment));
        binding.tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                segment = tab.getPosition();
                render();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

        accountViewModel.getMainCurrency().observe(getViewLifecycleOwner(), main -> {
            mainCurrency = main;
            render();
        });
        fundViewModel.getAllFunds().observe(getViewLifecycleOwner(), list -> {
            funds = list;
            render();
        });
        accountViewModel.getAllAccounts().observe(getViewLifecycleOwner(), list -> {
            accounts = list;
            render();
        });
        fundViewModel.getBalances().observe(getViewLifecycleOwner(), list -> {
            fundBalances = byId(list);
            render();
        });
        accountViewModel.getBalances().observe(getViewLifecycleOwner(), list -> {
            accountBalances = byId(list);
            render();
        });
        render();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_SEGMENT, segment);
    }

    static Map<Long, CurrencyTotals> byId(List<Balance> balances) {
        Map<Long, CurrencyTotals> totals = new HashMap<>();
        if (balances != null) {
            for (Balance balance : balances) {
                totals.computeIfAbsent(balance.id, id -> new CurrencyTotals()).add(balance.currency, balance.balance);
            }
        }
        return totals;
    }

    private void add() {
        if (segment == FUNDS) {
            FundForm.show(requireActivity(), null);
        } else if (segment == BUDGETS) {
            BudgetForm.show(requireActivity(), null);
        } else {
            AccountForm.show(requireActivity(), null);
        }
    }

    private void render() {
        if (binding == null) {
            return;
        }
        boolean budgets = segment == BUDGETS;
        binding.scrollList.setVisibility(budgets ? View.GONE : View.VISIBLE);
        binding.budgetsContainer.setVisibility(budgets ? View.VISIBLE : View.GONE);
        binding.fabAdd.setText(segment == FUNDS ? "Fund" : budgets ? "Budget" : "Account");
        if (budgets) {
            return;
        }

        ViewGroup list = binding.listRows;
        list.removeAllViews();
        CurrencyTotals net = new CurrencyTotals();
        if (segment == FUNDS) {
            for (int i = 0; i < funds.size(); i++) {
                Fund fund = funds.get(i);
                net.addAll(balanceOf(fundBalances, fund.getId()));
                list.addView(buildFundRow(list, fund, i == 0, i == funds.size() - 1));
            }
            binding.tvNetNote.setText("By what it's for. Accounts show the same total.");
            binding.tvHint.setText("Tap a fund for its history. Hold it to edit, make it your everyday fund or delete it.");
        } else {
            for (int i = 0; i < accounts.size(); i++) {
                Account account = accounts.get(i);
                net.addAll(balanceOf(accountBalances, account.getId()));
                list.addView(buildAccountRow(list, account, i == 0, i == accounts.size() - 1));
            }
            binding.tvNetNote.setText("By where it is. Funds show the same total.");
            binding.tvHint.setText("Tap an account for its history. Hold it to edit, set as default or delete it.");
        }
        binding.tvNetBalance.setText(net.format(mainCurrency, mainCurrency));
        binding.tvNetBalance.setTextColor(requireContext().getColor(net.allNegative() ? R.color.alert : R.color.on_surface));
    }

    private void explainTotal() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Net balance")
                .setMessage("Your money, added up, one amount for each currency. Funds and accounts are two ways "
                        + "of looking at the same money, so the total is the same in both.\n\n"
                        + "Accounts are where money is: a bank, a card, cash. Funds are what it's for: your "
                        + "everyday fund is day-to-day money, and budgets track what it spends. Move money into "
                        + "other funds to set it aside, and give a fund a target to save towards.\n\n"
                        + "Transactions you've left out of the summary don't count, and moving money between "
                        + "your own funds or accounts doesn't change the total.")
                .setPositiveButton("OK", null)
                .show();
    }

    private void reorder() {
        if (segment == BUDGETS) {
            Fragment child = getChildFragmentManager().findFragmentById(R.id.budgetsContainer);
            if (child instanceof BudgetsFragment) {
                ((BudgetsFragment) child).reorder();
            }
            return;
        }
        List<String> names = new ArrayList<>();
        List<Long> ids = new ArrayList<>();
        if (segment == FUNDS) {
            for (Fund fund : funds) {
                names.add(fund.getName());
                ids.add(fund.getId());
            }
        } else {
            for (Account account : accounts) {
                names.add(account.getName());
                ids.add(account.getId());
            }
        }
        boolean ordersFunds = segment == FUNDS;
        ReorderDialog.show(requireContext(), ordersFunds ? "Order funds" : "Order accounts", names, order -> {
            List<Long> ordered = new ArrayList<>();
            for (int index : order) {
                ordered.add(ids.get(index));
            }
            if (ordersFunds) {
                fundViewModel.saveOrder(ordered);
            } else {
                accountViewModel.saveOrder(ordered);
            }
        });
    }

    private static CurrencyTotals balanceOf(Map<Long, CurrencyTotals> balances, long id) {
        CurrencyTotals balance = balances.get(id);
        return balance != null ? balance : new CurrencyTotals();
    }

    private ItemAccountBinding inflateRow(ViewGroup list, String name, boolean first, boolean last) {
        ItemAccountBinding row = ItemAccountBinding.inflate(getLayoutInflater(), list, false);
        row.getRoot().setBackgroundResource(first && last ? R.drawable.bg_row_single
                : first ? R.drawable.bg_row_top : last ? R.drawable.bg_row_bottom : R.drawable.bg_row_middle);
        row.divider.setVisibility(last ? View.GONE : View.VISIBLE);
        row.tvName.setText(name);
        row.tvInitials.setText(initials(name));
        return row;
    }

    private View buildFundRow(ViewGroup list, Fund fund, boolean first, boolean last) {
        ItemAccountBinding row = inflateRow(list, fund.getName(), first, last);
        CurrencyTotals value = balanceOf(fundBalances, fund.getId());
        // A fund can hold several currencies; its target, like what it shows first, is in the main one
        String targetLine = FundForm.targetLine(fund, value.get(mainCurrency), mainCurrency);
        String detail = targetLine != null ? targetLine : FundForm.detailLine(fund);
        row.tvDetail.setText(detail);
        row.tvDetail.setVisibility(detail.isEmpty() ? View.GONE : View.VISIBLE);
        row.progressTarget.setVisibility(targetLine != null ? View.VISIBLE : View.GONE);
        row.progressTarget.setProgress(FundForm.targetPercent(fund, value.get(mainCurrency)));
        if (fund.isDefault()) {
            // The default fund is everyday money, the one budgets keep track of
            Ui.styleTag(row.tvDefault, "Everyday", R.color.primary_container, R.color.on_primary_container);
        }
        row.tvBalance.setText(value.format(mainCurrency, mainCurrency));
        row.tvBalance.setTextColor(requireContext().getColor(value.allNegative() ? R.color.alert : R.color.on_surface));

        row.getRoot().setOnClickListener(v -> openDetails(AccountDetailsFragment.KIND_FUND, fund.getId()));
        row.getRoot().setOnLongClickListener(v -> {
            showFundMenu(v, fund);
            return true;
        });
        return row.getRoot();
    }

    private View buildAccountRow(ViewGroup list, Account account, boolean first, boolean last) {
        ItemAccountBinding row = inflateRow(list, account.getName(), first, last);
        CurrencyTotals value = balanceOf(accountBalances, account.getId());
        String own = account.getCurrency() != null ? account.getCurrency() : mainCurrency;
        String detail = AccountForm.detailLine(account);
        row.tvDetail.setText(detail);
        row.tvDetail.setVisibility(detail.isEmpty() ? View.GONE : View.VISIBLE);
        row.progressTarget.setVisibility(View.GONE);
        if (account.isDefault()) {
            Ui.styleTag(row.tvDefault, "Default", R.color.primary_container, R.color.on_primary_container);
        }
        row.tvBalance.setText(value.format(own, own));
        row.tvBalance.setTextColor(requireContext().getColor(value.allNegative() ? R.color.alert : R.color.on_surface));

        row.getRoot().setOnClickListener(v -> openDetails(AccountDetailsFragment.KIND_ACCOUNT, account.getId()));
        row.getRoot().setOnLongClickListener(v -> {
            showAccountMenu(v, account);
            return true;
        });
        return row.getRoot();
    }

    private void openDetails(String kind, long id) {
        NavHostFragment.findNavController(this).navigate(R.id.account_details_fragment,
                AccountDetailsFragment.args(kind, id));
    }

    private void showFundMenu(View anchor, Fund fund) {
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        menu.getMenu().add(Menu.EDIT, Menu.EDIT, 0, "Edit");
        if (!fund.isDefault()) {
            menu.getMenu().add(Menu.SET_DEFAULT, Menu.SET_DEFAULT, 1, "Set as everyday fund");
        }
        menu.getMenu().add(Menu.DELETE, Menu.DELETE, 2, "Delete");
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == Menu.EDIT) {
                FundForm.show(requireActivity(), fund);
            } else if (item.getItemId() == Menu.SET_DEFAULT) {
                fundViewModel.setDefaultFund(fund);
            } else {
                FundForm.confirmDelete(requireActivity(), fund, null);
            }
            return true;
        });
        menu.show();
    }

    private void showAccountMenu(View anchor, Account account) {
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
                accountViewModel.setDefaultAccount(account);
            } else {
                AccountForm.confirmDelete(requireActivity(), account, null);
            }
            return true;
        });
        menu.show();
    }

    static String initials(String name) {
        String trimmed = name != null ? name.trim() : "";
        if (trimmed.isEmpty()) {
            return "";
        }
        String[] words = trimmed.split("\\s+");
        String letters = words.length > 1
                ? words[0].substring(0, 1) + words[1].substring(0, 1)
                : trimmed.substring(0, Math.min(2, trimmed.length()));
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
