package com.example.expensemate.viewmodel;

import android.app.Application;
import android.util.Log;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import com.example.expensemate.data.AppDatabase;
import com.example.expensemate.data.Transaction;
import com.example.expensemate.data.TransactionDao;
import com.example.expensemate.data.TotalExpense;
import com.example.expensemate.data.TotalExpenseDao;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TransactionViewModel extends AndroidViewModel {
    private static final String TAG = "TransactionViewModel";
    private final AppDatabase database;
    private final TransactionDao transactionDao;
    private final TotalExpenseDao totalExpenseDao;
    private final ExecutorService executorService;
    private final LiveData<List<Transaction>> allTransactions;
    private final LiveData<Double> totalExpense;

    public TransactionViewModel(Application application) {
        super(application);
        database = AppDatabase.getDatabase(application);
        transactionDao = database.transactionDao();
        totalExpenseDao = database.totalExpenseDao();
        executorService = Executors.newSingleThreadExecutor();
        allTransactions = transactionDao.getAllTransactions();
        
        // Initialize total expense if not exists
        executorService.execute(() -> {
            try {
                TotalExpense existing = totalExpenseDao.getTotalExpense().getValue();
                if (existing == null) {
                    Log.d(TAG, "Initializing total expense");
                    totalExpenseDao.insert(new TotalExpense(0.0));
                }
            } catch (Exception e) {
                Log.e(TAG, "Error initializing total expense", e);
            }
        });

        // Transform TotalExpense to Double
        totalExpense = Transformations.map(totalExpenseDao.getTotalExpense(), 
            totalExpense -> {
                Log.d(TAG, "Total expense updated: " + (totalExpense != null ? totalExpense.getAmount() : 0.0));
                return totalExpense != null ? totalExpense.getAmount() : 0.0;
            });
    }

    public LiveData<List<Transaction>> getAllTransactions() {
        return allTransactions;
    }

    public LiveData<List<Transaction>> getDebitTransactions() {
        return transactionDao.getDebitTransactions();
    }

    public LiveData<List<Transaction>> getTransactionsByAccount(String accountNumber) {
        return transactionDao.getTransactionsByAccount(accountNumber);
    }

    public void insertTransaction(Transaction transaction) {
        executorService.execute(() -> {
            try {
                Log.d(TAG, "Inserting transaction: " + transaction.getAmount() + " " + transaction.getTransactionType());
                transactionDao.insertTransaction(transaction);
                
                // Update total expense if it's a debit transaction
                if (transaction.getTransactionType().equals("DEBIT")) {
                    Log.d(TAG, "Updating total expense for debit transaction");
                    totalExpenseDao.incrementAmount(transaction.getAmount());
                }
            } catch (Exception e) {
                Log.e(TAG, "Error inserting transaction", e);
            }
        });
    }

    public void deleteTransaction(Transaction transaction) {
        executorService.execute(() -> {
            try {
                Log.d(TAG, "Deleting transaction: " + transaction.getAmount() + " " + transaction.getTransactionType());
                transactionDao.deleteTransaction(transaction);
                
                // Update total expense if it's a debit transaction
                if (transaction.getTransactionType().equals("DEBIT")) {
                    Log.d(TAG, "Updating total expense for deleted debit transaction");
                    totalExpenseDao.decrementAmount(transaction.getAmount());
                }
            } catch (Exception e) {
                Log.e(TAG, "Error deleting transaction", e);
            }
        });
    }

    public LiveData<Double> getTotalExpense() {
        return totalExpense;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executorService.shutdown();
    }
} 