package com.example.expensemate.ui.activity;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.expensemate.R;
import com.example.expensemate.data.Account;
import com.example.expensemate.service.MmsChangeJob;
import com.example.expensemate.data.Category;
import com.example.expensemate.data.Fund;
import com.example.expensemate.databinding.FragmentActivityBinding;
import com.example.expensemate.ui.common.ActivityAdapter;
import com.example.expensemate.ui.common.MoneyPicker;
import com.example.expensemate.ui.common.Pickers;
import com.example.expensemate.ui.common.RowSwipe;
import com.example.expensemate.ui.common.Ui;
import com.example.expensemate.viewmodel.AccountViewModel;
import com.example.expensemate.viewmodel.ActivityRow;
import com.example.expensemate.viewmodel.CategoryViewModel;
import com.example.expensemate.viewmodel.FundViewModel;
import com.example.expensemate.viewmodel.TransactionFilter;
import com.example.expensemate.viewmodel.TransactionViewModel;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ActivityFragment extends Fragment {
    private static final long SEARCH_DELAY_MS = 250;

    private FragmentActivityBinding binding;
    private TransactionViewModel viewModel;
    private List<Fund> funds = new ArrayList<>();
    private List<Account> accounts = new ArrayList<>();
    private List<Category> categories = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;
    private TransactionFilter shownFilter;
    private boolean refreshing; // A message check is running, so another tap only reloads the list

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentActivityBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        ViewModelProvider provider = new ViewModelProvider(requireActivity());
        viewModel = provider.get(TransactionViewModel.class);

        ActivityAdapter adapter = new ActivityAdapter(this::openRow);
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerView.setAdapter(adapter);
        RowSwipe.attach(binding.recyclerView, adapter, new RowSwipe.Actions() {
            @Override
            public void onDelete(ActivityRow row) {
                Ui.deleteWithUndo(requireActivity(), viewModel, row.transaction);
            }

            @Override
            public void onOpen(ActivityRow row) {
                openRow(row);
            }
        });

        binding.fabAdd.setOnClickListener(v -> TransactionFormSheet.showAdd(requireActivity()));
        Ui.addAction(this, R.id.action_refresh, "Refresh", R.drawable.ic_refresh, this::refresh);
        binding.swipeRefresh.setOnRefreshListener(this::refresh);
        binding.swipeRefresh.setColorSchemeResources(R.color.primary);
        binding.swipeRefresh.setProgressBackgroundColorSchemeResource(R.color.surface);
        setChipAction(binding.chipRange, this::chooseRange);
        setChipAction(binding.chipAccount, this::chooseAccount);
        setChipAction(binding.chipCategory, this::chooseCategory);
        binding.chipMore.setOnClickListener(v -> {
            renderFilter(viewModel.getFilter().getValue());
            new FilterSheet().show(getChildFragmentManager(), "filters");
        });
        binding.chipClear.setOnClickListener(v -> {
            binding.etSearch.setText("");
            viewModel.clearFilter();
        });
        binding.btnClearSearch.setOnClickListener(v -> binding.etSearch.setText(""));

        String currentSearch = viewModel.getFilter().getValue().search;
        if (currentSearch != null) {
            binding.etSearch.setText(currentSearch);
        }
        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                onSearchChanged(s.toString().trim());
            }
        });

        provider.get(FundViewModel.class).getAllFunds().observe(getViewLifecycleOwner(), list -> {
            funds = list;
            adapter.setFunds(list);
            renderFilter(viewModel.getFilter().getValue());
        });
        provider.get(AccountViewModel.class).getAllAccounts().observe(getViewLifecycleOwner(), list -> {
            accounts = list;
            adapter.setAccounts(list);
            renderFilter(viewModel.getFilter().getValue());
        });
        provider.get(CategoryViewModel.class).getAllCategories().observe(getViewLifecycleOwner(), list -> categories = list);
        viewModel.getFilter().observe(getViewLifecycleOwner(), this::renderFilter);
        viewModel.getActivityRows().observe(getViewLifecycleOwner(), rows -> {
            TransactionFilter filter = viewModel.getFilter().getValue();
            adapter.submit(rows, filter != shownFilter, viewModel.justAdded(), binding.recyclerView);
            shownFilter = filter;
            binding.emptyState.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    /**
     * Brings the list up to now: a range such as "Last 30 days" was worked out when it was picked, so it
     * moves on to end today, and bank messages that arrived while the app was asleep are read.
     */
    private void refresh() {
        TransactionFilter filter = viewModel.getFilter().getValue().copy();
        moveRangeToNow(filter);
        viewModel.setFilter(filter);
        if (refreshing) {
            return;
        }
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            binding.swipeRefresh.setRefreshing(false);
            Ui.snackbar(requireActivity(), "Refreshed. Turn on SMS reading in Settings to pick up bank messages.");
            return;
        }
        // The spinner shows while messages are read, whether the list was pulled or the button tapped
        refreshing = true;
        binding.swipeRefresh.setRefreshing(true);
        MmsChangeJob.start(requireContext().getApplicationContext(), () -> handler.post(() -> {
            refreshing = false;
            if (binding != null && isAdded()) {
                binding.swipeRefresh.setRefreshing(false);
                Ui.snackbar(requireActivity(), "Up to date");
            }
        }));
    }

    private static void moveRangeToNow(TransactionFilter filter) {
        java.util.regex.Matcher lastDays = java.util.regex.Pattern.compile("Last (\\d+) days")
                .matcher(filter.rangeLabel != null ? filter.rangeLabel : "");
        if (lastDays.matches()) {
            filter.setLastDays(Integer.parseInt(lastDays.group(1)));
        } else if ("This month".equals(filter.rangeLabel)) {
            setThisMonth(filter);
        }
    }

    private void openRow(ActivityRow row) {
        TransactionDetailsSheet.show(requireActivity(), row.transaction.getId());
    }

    /** Taps on a chip and on its arrow do the same thing, and the chip's state only follows the filter. */
    private void setChipAction(Chip chip, Runnable action) {
        View.OnClickListener listener = v -> {
            renderFilter(viewModel.getFilter().getValue());
            action.run();
        };
        chip.setOnClickListener(listener);
        chip.setOnCloseIconClickListener(listener);
    }

    private void onSearchChanged(String text) {
        binding.btnClearSearch.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
        if (pendingSearch != null) {
            handler.removeCallbacks(pendingSearch);
        }
        pendingSearch = () -> {
            TransactionFilter filter = viewModel.getFilter().getValue().copy();
            filter.search = text.isEmpty() ? null : text;
            viewModel.setFilter(filter);
        };
        handler.postDelayed(pendingSearch, SEARCH_DELAY_MS);
    }

    private void renderFilter(TransactionFilter filter) {
        if (binding == null || filter == null) {
            return;
        }
        binding.chipRange.setText(filter.rangeLabel);
        binding.chipRange.setChecked(true);

        String shownName = null;
        for (Fund fund : funds) {
            if (filter.fundId != null && fund.getId() == filter.fundId) {
                shownName = fund.getName();
            }
        }
        for (Account account : accounts) {
            if (filter.accountId != null && account.getId() == filter.accountId) {
                shownName = account.getName();
            }
        }
        binding.chipAccount.setText(shownName != null ? shownName : "Fund or account");
        binding.chipAccount.setChecked(filter.fundId != null || filter.accountId != null);

        binding.chipCategory.setText(filter.category != null ? filter.category : "Category");
        binding.chipCategory.setChecked(filter.category != null);

        binding.chipMore.setText(Boolean.TRUE.equals(filter.needsReview) ? "Needs a category" : "More");
        binding.chipMore.setChecked(filter.hasMoreFilters());
        binding.chipClear.setVisibility(filter.isCustomized() ? View.VISIBLE : View.GONE);
    }

    private void chooseRange() {
        String[] options = { "Last 7 days", "Last 30 days", "Last 90 days", "This month", "All time", "Custom dates" };
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Date range")
                .setItems(options, (dialog, which) -> {
                    TransactionFilter filter = viewModel.getFilter().getValue().copy();
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
                            setThisMonth(filter);
                            break;
                        case 4:
                            filter.setAllTime();
                            break;
                        default:
                            chooseCustomRange();
                            return;
                    }
                    viewModel.setFilter(filter);
                })
                .show();
    }

    private static void setThisMonth(TransactionFilter filter) {
        Calendar start = Calendar.getInstance();
        start.set(Calendar.DAY_OF_MONTH, 1);
        TransactionFilter.startOfDay(start);
        Calendar end = Calendar.getInstance();
        TransactionFilter.endOfDay(end);
        filter.fromMillis = start.getTimeInMillis();
        filter.toMillis = end.getTimeInMillis();
        filter.rangeLabel = "This month";
    }

    private void chooseCustomRange() {
        Pickers.pickRange(getChildFragmentManager(), (from, to) -> {
            TransactionFilter filter = viewModel.getFilter().getValue().copy();
            SimpleDateFormat format = new SimpleDateFormat("d MMM", Locale.getDefault());
            filter.fromMillis = from;
            filter.toMillis = to;
            filter.rangeLabel = format.format(new Date(from)) + " – " + format.format(new Date(to));
            viewModel.setFilter(filter);
        });
    }

    private void chooseAccount() {
        TransactionFilter current = viewModel.getFilter().getValue();
        MoneyPicker.show(requireContext(), "Show", "Everything", funds, accounts, current.fundId, current.accountId,
                (fundId, accountId) -> {
                    TransactionFilter filter = viewModel.getFilter().getValue().copy();
                    filter.fundId = fundId;
                    filter.accountId = accountId;
                    viewModel.setFilter(filter);
                });
    }

    private void chooseCategory() {
        Set<String> unique = new LinkedHashSet<>();
        for (Category category : categories) {
            unique.add(category.getName());
        }
        List<String> names = new ArrayList<>();
        names.add("All categories");
        names.addAll(unique);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Category")
                .setItems(names.toArray(new String[0]), (dialog, which) -> {
                    TransactionFilter filter = viewModel.getFilter().getValue().copy();
                    filter.category = which == 0 ? null : names.get(which);
                    viewModel.setFilter(filter);
                })
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (pendingSearch != null) {
            handler.removeCallbacks(pendingSearch);
        }
        binding = null;
    }
}
