package com.example.expensemate.ui.expense;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.example.expensemate.R;
import com.example.expensemate.data.Account;
import com.example.expensemate.viewmodel.AccountViewModel;
import com.example.expensemate.viewmodel.TransactionViewModel;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class ExpenseFragment extends Fragment {
    private static final String TAG = "ExpenseFragment";
    private TransactionViewModel transactionViewModel;
    private AccountViewModel accountViewModel;
    private TextView totalExpenseText;
    private TextView totalIncomeText;
    private TextView totalBalanceText;
    private TextView periodText;
    private ImageButton prevMonthButton;
    private ImageButton nextMonthButton;
    private AutoCompleteTextView accountDropdown;
    private final NumberFormat formatter = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
    private Calendar currentPeriod;
    private List<Account> accounts = new ArrayList<>();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_expense, container, false);
        
        totalExpenseText = root.findViewById(R.id.total_expense);
        totalIncomeText = root.findViewById(R.id.total_income);
        totalBalanceText = root.findViewById(R.id.total_balance);
        periodText = root.findViewById(R.id.tv_period);
        prevMonthButton = root.findViewById(R.id.btn_prev_month);
        nextMonthButton = root.findViewById(R.id.btn_next_month);
        accountDropdown = root.findViewById(R.id.account_dropdown);
        
        transactionViewModel = new ViewModelProvider(requireActivity()).get(TransactionViewModel.class);
        accountViewModel = new ViewModelProvider(requireActivity()).get(AccountViewModel.class);
        
        // Initialize current period
        currentPeriod = Calendar.getInstance();
        updatePeriodDisplay();
        updateSelectedPeriod();
        
        // Set up month navigation
        prevMonthButton.setOnClickListener(v -> {
            currentPeriod.add(Calendar.MONTH, -1);
            updatePeriodDisplay();
            updateSelectedPeriod();
        });
        
        nextMonthButton.setOnClickListener(v -> {
            currentPeriod.add(Calendar.MONTH, 1);
            updatePeriodDisplay();
            updateSelectedPeriod();
        });

        // Set up account dropdown
        accountViewModel.getAllAccounts().observe(getViewLifecycleOwner(), accountList -> {
            accounts = accountList;
            List<String> accountNames = new ArrayList<>();
            for (Account account : accounts) {
                accountNames.add(account.getName());
            }
            ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                accountNames
            ) {
                @Override
                public View getView(int position, View convertView, ViewGroup parent) {
                    View view = super.getView(position, convertView, parent);
                    TextView text = (TextView) view.findViewById(android.R.id.text1);
                    text.setTextColor(requireContext().getResources().getColor(R.color.black));
                    return view;
                }
            };
            accountDropdown.setAdapter(adapter);
            accountDropdown.setDropDownBackgroundResource(android.R.color.white);
        });

        // Observe default account
        accountViewModel.getDefaultAccount().observe(getViewLifecycleOwner(), defaultAccount -> {
            if (defaultAccount != null) {
                accountDropdown.setText(defaultAccount.getName(), false);
                transactionViewModel.setSelectedAccount(defaultAccount.getId());
            }
        });

        // Handle account selection
        accountDropdown.setOnItemClickListener((parent, view, position, id) -> {
            Account selectedAccount = accounts.get(position);
            Log.d(TAG, "Account selected: " + selectedAccount.getName());
            transactionViewModel.setSelectedAccount(selectedAccount.getId());
        });
        
        // Observe total expense
        transactionViewModel.getTotalExpense().observe(getViewLifecycleOwner(), total -> {
            Log.d(TAG, "Total expense changed: " + total);
            totalExpenseText.setText(formatter.format(total != null ? total : 0.0));
            updateBalance();
        });

        // Observe total income
        transactionViewModel.getTotalIncome().observe(getViewLifecycleOwner(), total -> {
            Log.d(TAG, "Total income changed: " + total);
            totalIncomeText.setText(formatter.format(total != null ? total : 0.0));
            updateBalance();
        });

        return root;
    }

    private void updatePeriodDisplay() {
        SimpleDateFormat monthYearFormat = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
        periodText.setText(monthYearFormat.format(currentPeriod.getTime()));
    }

    private void updateSelectedPeriod() {
        SimpleDateFormat monthFormat = new SimpleDateFormat("MM", Locale.getDefault());
        SimpleDateFormat yearFormat = new SimpleDateFormat("yyyy", Locale.getDefault());
        String month = monthFormat.format(currentPeriod.getTime());
        String year = yearFormat.format(currentPeriod.getTime());
        Log.d(TAG, "Updating period to: " + month + "/" + year);
        transactionViewModel.setSelectedMonthYear(month, year);
    }

    private void updateBalance() {
        Double income = transactionViewModel.getTotalIncome().getValue();
        Double expense = transactionViewModel.getTotalExpense().getValue();
        
        double balance = (income != null ? income : 0.0) - (expense != null ? expense : 0.0);
        String formattedBalance = formatter.format(balance);
        
        // Set color based on whether balance is positive or negative
        int colorResId = balance >= 0 ? R.color.credit_color : R.color.debit_color;
        totalBalanceText.setTextColor(requireContext().getColor(colorResId));
        
        totalBalanceText.setText(formattedBalance);
        Log.d(TAG, "Balance updated: " + formattedBalance);
    }
} 