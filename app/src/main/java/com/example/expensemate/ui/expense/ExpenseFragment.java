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

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_expense, container, false);
        
        totalExpenseText = root.findViewById(R.id.total_expense);
        
        transactionViewModel = new ViewModelProvider(requireActivity()).get(TransactionViewModel.class);
        
        // Observe total expense
        transactionViewModel.getTotalExpense().observe(getViewLifecycleOwner(), total -> {
            Log.d(TAG, "Total expense changed: " + total);
            NumberFormat formatter = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
            String formattedAmount = formatter.format(total);
            Log.d(TAG, "Formatted amount: " + formattedAmount);
            totalExpenseText.setText(formattedAmount);
        });

        return root;
    }
} 