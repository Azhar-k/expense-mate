package com.example.expensemate.ui.accounts;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.expensemate.R;
import com.example.expensemate.data.Account;
import com.example.expensemate.data.Balance;
import com.example.expensemate.data.Fund;
import com.example.expensemate.databinding.FragmentAccountDetailsBinding;
import com.example.expensemate.ui.activity.TransactionDetailsSheet;
import com.example.expensemate.ui.activity.TransactionFormSheet;
import com.example.expensemate.ui.common.ActivityAdapter;
import com.example.expensemate.ui.common.Pickers;
import com.example.expensemate.ui.common.RowSwipe;
import com.example.expensemate.ui.common.Ui;
import com.example.expensemate.util.Currencies;
import com.example.expensemate.util.CurrencyTotals;
import com.example.expensemate.viewmodel.AccountViewModel;
import com.example.expensemate.viewmodel.ActivityRow;
import com.example.expensemate.viewmodel.FundViewModel;
import com.example.expensemate.viewmodel.TransactionFilter;
import com.example.expensemate.viewmodel.TransactionViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** The history and balance of one fund or one account. Their ids can be equal, so the kind is always passed. */
public class AccountDetailsFragment extends Fragment {
    public static final String ARG_KIND = "kind";
    public static final String ARG_ID = "id";
    public static final String KIND_FUND = "fund";
    public static final String KIND_ACCOUNT = "account";

    private static final int MENU_EDIT = 1;
    private static final int MENU_DEFAULT = 2;
    private static final int MENU_DELETE = 3;

    private FragmentAccountDetailsBinding binding;
    private AccountViewModel accountViewModel;
    private FundViewModel fundViewModel;
    private TransactionViewModel transactionViewModel;
    private final MutableLiveData<TransactionFilter> range = new MutableLiveData<>(TransactionFilter.lastDays(30));
    private boolean isFund;
    private long id;
    private Fund fund;
    private Account account;
    private String mainCurrency = Currencies.fromLocale();
    private List<Balance> lastBalances = new ArrayList<>();
    private TransactionFilter shownRange;

    public static Bundle args(String kind, long id) {
        Bundle args = new Bundle();
        args.putString(ARG_KIND, kind);
        args.putLong(ARG_ID, id);
        return args;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentAccountDetailsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        isFund = KIND_FUND.equals(requireArguments().getString(ARG_KIND));
        id = requireArguments().getLong(ARG_ID);
        ViewModelProvider provider = new ViewModelProvider(requireActivity());
        accountViewModel = provider.get(AccountViewModel.class);
        fundViewModel = provider.get(FundViewModel.class);
        transactionViewModel = provider.get(TransactionViewModel.class);

        requireActivity().addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
                menu.add(Menu.NONE, MENU_EDIT, Menu.NONE, "Edit");
                menu.add(Menu.NONE, MENU_DEFAULT, Menu.NONE, isFund ? "Set as everyday fund" : "Set as default");
                menu.add(Menu.NONE, MENU_DELETE, Menu.NONE, "Delete");
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem item) {
                return isFund ? onFundMenu(item.getItemId()) : onAccountMenu(item.getItemId());
            }
        }, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
        // Starts from what is on screen: a move out of this fund, or a transfer out of this account
        if (isFund) {
            Ui.addAction(this, R.id.action_move, "Move money", R.drawable.ic_move,
                    () -> TransactionFormSheet.showMoveOrTransfer(requireActivity(), id, null));
        } else {
            Ui.addAction(this, R.id.action_move, "Transfer", R.drawable.ic_swap,
                    () -> TransactionFormSheet.showMoveOrTransfer(requireActivity(), null, id));
        }

        ActivityAdapter adapter = new ActivityAdapter(
                row -> TransactionDetailsSheet.show(requireActivity(), row.transaction.getId()));
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerView.setAdapter(adapter);
        RowSwipe.attach(binding.recyclerView, adapter, new RowSwipe.Actions() {
            @Override
            public void onDelete(ActivityRow row) {
                Ui.deleteWithUndo(requireActivity(), transactionViewModel, row.transaction);
            }

            @Override
            public void onOpen(ActivityRow row) {
                TransactionDetailsSheet.show(requireActivity(), row.transaction.getId());
            }
        });

        binding.chipRange.setOnClickListener(v -> chooseRange());
        accountViewModel.getMainCurrency().observe(getViewLifecycleOwner(), main -> {
            mainCurrency = main;
            renderBalance(lastBalances);
        });
        fundViewModel.getAllFunds().observe(getViewLifecycleOwner(), funds -> {
            adapter.setFunds(funds);
            if (isFund) {
                for (Fund candidate : funds) {
                    if (candidate.getId() == id) {
                        fund = candidate;
                        updateHeader();
                    }
                }
                renderBalance(lastBalances);
            }
        });
        accountViewModel.getAllAccounts().observe(getViewLifecycleOwner(), accounts -> {
            adapter.setAccounts(accounts);
            if (!isFund) {
                for (Account candidate : accounts) {
                    if (candidate.getId() == id) {
                        account = candidate;
                        updateHeader();
                    }
                }
                renderBalance(lastBalances);
            }
        });
        (isFund ? fundViewModel.getBalances() : accountViewModel.getBalances())
                .observe(getViewLifecycleOwner(), this::renderBalance);

        LiveData<List<ActivityRow>> rows = Transformations.switchMap(range, r -> isFund
                ? transactionViewModel.getRowsForFund(id, r.fromMillis, r.toMillis)
                : transactionViewModel.getRowsForAccount(id, r.fromMillis, r.toMillis));
        rows.observe(getViewLifecycleOwner(), list -> {
            adapter.submit(list, range.getValue() != shownRange, transactionViewModel.justAdded(), binding.recyclerView);
            shownRange = range.getValue();
            binding.tvEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        });
        range.observe(getViewLifecycleOwner(), r -> binding.chipRange.setText(r.rangeLabel));
    }

    private boolean onFundMenu(int itemId) {
        if (fund == null) {
            return false;
        }
        switch (itemId) {
            case MENU_EDIT:
                FundForm.show(requireActivity(), fund);
                return true;
            case MENU_DEFAULT:
                fundViewModel.setDefaultFund(fund);
                Ui.snackbar(requireActivity(), fund.getName() + " is now your everyday fund");
                return true;
            case MENU_DELETE:
                FundForm.confirmDelete(requireActivity(), fund,
                        () -> NavHostFragment.findNavController(this).navigateUp());
                return true;
            default:
                return false;
        }
    }

    private boolean onAccountMenu(int itemId) {
        if (account == null) {
            return false;
        }
        switch (itemId) {
            case MENU_EDIT:
                AccountForm.show(requireActivity(), account);
                return true;
            case MENU_DEFAULT:
                accountViewModel.setDefaultAccount(account);
                Ui.snackbar(requireActivity(), account.getName() + " is now your default account");
                return true;
            case MENU_DELETE:
                AccountForm.confirmDelete(requireActivity(), account,
                        () -> NavHostFragment.findNavController(this).navigateUp());
                return true;
            default:
                return false;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updateHeader();
    }

    private void updateHeader() {
        if (binding == null || (isFund ? fund == null : account == null)) {
            return;
        }
        if (isResumed()) {
            ((AppCompatActivity) requireActivity()).getSupportActionBar()
                    .setTitle(isFund ? fund.getName() : account.getName());
        }
        String detail = isFund ? FundForm.detailLine(fund) : AccountForm.detailLine(account);
        binding.tvDetail.setText(detail);
        binding.tvDetail.setVisibility(detail.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void renderBalance(List<Balance> balances) {
        lastBalances = balances != null ? balances : new ArrayList<>();
        if (binding == null || (isFund ? fund == null : account == null)) {
            return;
        }
        CurrencyTotals value = new CurrencyTotals();
        for (Balance balance : lastBalances) {
            if (balance.id == id) {
                value.add(balance.currency, balance.balance);
            }
        }
        // An account is in its own currency; a fund shows each currency it holds, the main one first
        String own = !isFund && account.getCurrency() != null ? account.getCurrency() : mainCurrency;
        binding.tvBalance.setText(value.format(own, own));
        binding.tvBalance.setTextColor(requireContext().getColor(value.allNegative() ? R.color.alert : R.color.on_surface));
        if (isFund) {
            // Saving towards something: say how far it has got
            String targetLine = FundForm.targetLine(fund, value.get(mainCurrency), mainCurrency);
            if (targetLine != null) {
                binding.tvDetail.setText(targetLine);
                binding.tvDetail.setVisibility(View.VISIBLE);
            }
        }
    }

    private void chooseRange() {
        String[] options = { "Last 7 days", "Last 30 days", "Last 90 days", "All time", "Custom dates" };
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Date range")
                .setItems(options, (dialog, which) -> {
                    TransactionFilter filter = new TransactionFilter();
                    switch (which) {
                        case 0:
                            filter.setLastDays(7);
                            break;
                        case 1:
                            filter.setLastDays(30);
                            break;
                        case 2:
                            filter.setLastDays(90);
                            break;
                        case 3:
                            filter.setAllTime();
                            break;
                        default:
                            Pickers.pickRange(getChildFragmentManager(), (from, to) -> {
                                TransactionFilter custom = new TransactionFilter();
                                SimpleDateFormat format = new SimpleDateFormat("d MMM", Locale.getDefault());
                                custom.fromMillis = from;
                                custom.toMillis = to;
                                custom.rangeLabel = format.format(new Date(from)) + " – " + format.format(new Date(to));
                                range.setValue(custom);
                            });
                            return;
                    }
                    range.setValue(filter);
                })
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
