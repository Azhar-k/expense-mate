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
import com.example.expensemate.data.AccountBalance;
import com.example.expensemate.databinding.FragmentAccountDetailsBinding;
import com.example.expensemate.ui.activity.TransactionDetailsSheet;
import com.example.expensemate.ui.common.ActivityAdapter;
import com.example.expensemate.ui.common.Pickers;
import com.example.expensemate.ui.common.RowSwipe;
import com.example.expensemate.ui.common.Ui;
import com.example.expensemate.util.Money;
import com.example.expensemate.viewmodel.AccountViewModel;
import com.example.expensemate.viewmodel.ActivityRow;
import com.example.expensemate.viewmodel.TransactionFilter;
import com.example.expensemate.viewmodel.TransactionViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AccountDetailsFragment extends Fragment {
    private static final int MENU_EDIT = 1;
    private static final int MENU_DEFAULT = 2;
    private static final int MENU_DELETE = 3;

    private FragmentAccountDetailsBinding binding;
    private AccountViewModel accountViewModel;
    private TransactionViewModel transactionViewModel;
    private final MutableLiveData<TransactionFilter> range = new MutableLiveData<>(TransactionFilter.lastDays(30));
    private long accountId;
    private Account account;
    private TransactionFilter shownRange;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentAccountDetailsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        accountId = requireArguments().getLong("accountId");
        ViewModelProvider provider = new ViewModelProvider(requireActivity());
        accountViewModel = provider.get(AccountViewModel.class);
        transactionViewModel = provider.get(TransactionViewModel.class);

        requireActivity().addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
                menu.add(Menu.NONE, MENU_EDIT, Menu.NONE, "Edit");
                menu.add(Menu.NONE, MENU_DEFAULT, Menu.NONE, "Set as default");
                menu.add(Menu.NONE, MENU_DELETE, Menu.NONE, "Delete");
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem item) {
                if (account == null) {
                    return false;
                }
                switch (item.getItemId()) {
                    case MENU_EDIT:
                        AccountForm.show(requireActivity(), account);
                        return true;
                    case MENU_DEFAULT:
                        accountViewModel.setDefaultAccount(account.getId());
                        Ui.snackbar(requireActivity(), account.getName() + " is now your default account");
                        return true;
                    case MENU_DELETE:
                        AccountForm.confirmDelete(requireActivity(), account,
                                () -> NavHostFragment.findNavController(AccountDetailsFragment.this).navigateUp());
                        return true;
                    default:
                        return false;
                }
            }
        }, getViewLifecycleOwner(), Lifecycle.State.RESUMED);

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
        accountViewModel.getAllAccounts().observe(getViewLifecycleOwner(), accounts -> {
            adapter.setAccounts(accounts);
            for (Account candidate : accounts) {
                if (candidate.getId() == accountId) {
                    account = candidate;
                    updateHeader();
                }
            }
        });
        transactionViewModel.getAccountBalances().observe(getViewLifecycleOwner(), this::renderBalance);

        LiveData<List<ActivityRow>> rows = Transformations.switchMap(range,
                r -> transactionViewModel.getRowsForAccount(accountId, r.fromMillis, r.toMillis));
        rows.observe(getViewLifecycleOwner(), list -> {
            adapter.submit(list, range.getValue() != shownRange, binding.recyclerView);
            shownRange = range.getValue();
            binding.tvEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        });
        range.observe(getViewLifecycleOwner(), r -> binding.chipRange.setText(r.rangeLabel));
    }

    @Override
    public void onResume() {
        super.onResume();
        updateHeader();
    }

    private void updateHeader() {
        if (account == null || binding == null) {
            return;
        }
        if (isResumed()) {
            ((AppCompatActivity) requireActivity()).getSupportActionBar().setTitle(account.getName());
        }
        String detail = AccountForm.detailLine(account);
        binding.tvDetail.setText(detail);
        binding.tvDetail.setVisibility(detail.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void renderBalance(List<AccountBalance> balances) {
        double value = 0;
        for (AccountBalance balance : balances) {
            if (balance.accountId == accountId) {
                value = balance.balance;
            }
        }
        binding.tvBalance.setText(Money.format(value));
        binding.tvBalance.setTextColor(requireContext().getColor(value < 0 ? R.color.alert : R.color.on_surface));
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
