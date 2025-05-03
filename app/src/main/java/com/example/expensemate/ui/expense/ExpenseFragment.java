package com.example.expensemate.ui.expense;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.example.expensemate.R;
import com.example.expensemate.viewmodel.TransactionViewModel;
import java.text.NumberFormat;
import java.util.Locale;

public class ExpenseFragment extends Fragment {
    private static final String TAG = "ExpenseFragment";
    private TransactionViewModel transactionViewModel;
    private TextView totalExpenseText;
    private TextView totalIncomeText;
    private TextView totalBalanceText;
    private final NumberFormat formatter = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_expense, container, false);
        
        totalExpenseText = root.findViewById(R.id.total_expense);
        totalIncomeText = root.findViewById(R.id.total_income);
        totalBalanceText = root.findViewById(R.id.total_balance);
        
        transactionViewModel = new ViewModelProvider(requireActivity()).get(TransactionViewModel.class);
        
        // Observe total expense
        transactionViewModel.getTotalExpense().observe(getViewLifecycleOwner(), total -> {
            Log.d(TAG, "Total expense changed: " + total);
            String formattedAmount = formatter.format(total);
            Log.d(TAG, "Formatted amount: " + formattedAmount);
            totalExpenseText.setText(formattedAmount);
            updateBalance();
        });

        // Observe total income
        transactionViewModel.getTotalIncome().observe(getViewLifecycleOwner(), total -> {
            Log.d(TAG, "Total income changed: " + total);
            String formattedAmount = formatter.format(total);
            Log.d(TAG, "Formatted amount: " + formattedAmount);
            totalIncomeText.setText(formattedAmount);
            updateBalance();
        });

        return root;
    }

    private void updateBalance() {
        Double income = transactionViewModel.getTotalIncome().getValue();
        Double expense = transactionViewModel.getTotalExpense().getValue();
        
        if (income != null && expense != null) {
            double balance = income - expense;
            String formattedBalance = formatter.format(balance);
            
            // Set color based on whether balance is positive or negative
            int colorResId = balance >= 0 ? R.color.credit_color : R.color.debit_color;
            totalBalanceText.setTextColor(requireContext().getColor(colorResId));
            
            totalBalanceText.setText(formattedBalance);
            Log.d(TAG, "Balance updated: " + formattedBalance);
        }
    }
} 