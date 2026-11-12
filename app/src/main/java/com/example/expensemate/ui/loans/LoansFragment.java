package com.example.expensemate.ui.loans;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.expensemate.R;
import com.example.expensemate.data.LoanSummary;
import com.example.expensemate.databinding.FragmentLoansBinding;
import com.example.expensemate.databinding.ItemLoanRowBinding;
import com.example.expensemate.ui.common.Ui;
import com.example.expensemate.util.Currencies;
import com.example.expensemate.util.CurrencyTotals;
import com.example.expensemate.util.Money;
import com.example.expensemate.viewmodel.AccountViewModel;
import com.example.expensemate.viewmodel.LoanViewModel;

import java.util.ArrayList;
import java.util.List;

/** Money lent to people: what is still out, and what has come back. */
public class LoansFragment extends Fragment {
    private FragmentLoansBinding binding;
    private String mainCurrency = Currencies.fromLocale();
    private List<LoanSummary> shown = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentLoansBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        binding.fabAdd.setOnClickListener(v -> LoanForm.showAdd(requireActivity()));
        ViewModelProvider provider = new ViewModelProvider(requireActivity());
        provider.get(AccountViewModel.class).getMainCurrency().observe(getViewLifecycleOwner(), main -> {
            mainCurrency = main;
            render(shown);
        });
        provider.get(LoanViewModel.class).getLoans().observe(getViewLifecycleOwner(), this::render);
    }

    private void render(List<LoanSummary> loans) {
        shown = loans;
        if (binding == null) {
            return;
        }
        List<LoanSummary> open = new ArrayList<>();
        List<LoanSummary> completed = new ArrayList<>();
        CurrencyTotals outstanding = new CurrencyTotals();
        for (LoanSummary summary : loans) {
            if (summary.isCompleted()) {
                completed.add(summary);
            } else {
                open.add(summary);
                outstanding.add(summary.loan.getCurrency(), summary.outstanding());
            }
        }
        binding.tvOutstanding.setText(outstanding.format(mainCurrency, mainCurrency));
        Ui.styleTag(binding.tvOpenCount, open.size() + " open", R.color.primary_container,
                R.color.on_primary_container);

        fillList(binding.listOpen, open,
                "Nothing lent right now. Add it here, or from a payment in Activity with Track as lent.");
        fillList(binding.listCompleted, completed, null);
        binding.headerCompleted.setVisibility(completed.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void fillList(LinearLayout list, List<LoanSummary> loans, @Nullable String emptyText) {
        list.removeAllViews();
        if (loans.isEmpty() && emptyText != null) {
            TextView empty = (TextView) getLayoutInflater().inflate(R.layout.item_list_empty, list, false);
            empty.setText(emptyText);
            list.addView(empty);
        }
        for (int i = 0; i < loans.size(); i++) {
            list.addView(buildRow(list, loans.get(i), i == 0, i == loans.size() - 1));
        }
    }

    private View buildRow(LinearLayout parent, LoanSummary summary, boolean first, boolean last) {
        ItemLoanRowBinding row = ItemLoanRowBinding.inflate(getLayoutInflater(), parent, false);
        boolean completed = summary.isCompleted();
        row.getRoot().setBackgroundResource(first && last ? R.drawable.bg_row_single
                : first ? R.drawable.bg_row_top : last ? R.drawable.bg_row_bottom : R.drawable.bg_row_middle);
        row.divider.setVisibility(last ? View.GONE : View.VISIBLE);

        if (completed) {
            Ui.styleIconTile(row.ivIcon, R.drawable.ic_check, R.color.primary_container, R.color.primary);
        } else {
            Ui.styleIconTile(row.ivIcon, R.drawable.ic_handshake, R.color.surface_variant, R.color.on_surface);
        }
        row.tvPerson.setText(summary.loan.getPerson());
        row.tvStatus.setText(LoanText.statusLine(summary));
        row.tvAmount.setText(Money.format(completed ? summary.loan.getAmount() : summary.outstanding(), summary.loan.getCurrency()));
        row.tvAmount.setTextColor(ContextCompat.getColor(requireContext(),
                completed ? R.color.on_surface_variant : R.color.on_surface));
        row.progressReturned.setVisibility(completed || summary.returned <= 0 ? View.GONE : View.VISIBLE);
        row.progressReturned.setProgress(LoanText.progress(summary));
        row.getRoot().setOnClickListener(v -> LoanDetailsSheet.show(requireActivity(), summary.loan.getId()));
        return row.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
