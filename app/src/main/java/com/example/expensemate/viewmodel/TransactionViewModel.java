package com.example.expensemate.viewmodel;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import com.example.expensemate.data.AppDatabase;
import com.example.expensemate.data.Transaction;
import com.example.expensemate.data.TransactionDao;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TransactionViewModel extends AndroidViewModel {
    private final AppDatabase database;
    private final TransactionDao transactionDao;
    private final ExecutorService executorService;

    public TransactionViewModel(Application application) {
        super(application);
        database = AppDatabase.getDatabase(application);
        transactionDao = database.transactionDao();
        executorService = Executors.newSingleThreadExecutor();
    }

    public LiveData<List<Transaction>> getAllTransactions() {
        return transactionDao.getAllTransactions();
    }

    public LiveData<List<Transaction>> getDebitTransactions() {
        return transactionDao.getDebitTransactions();
    }

    public LiveData<List<Transaction>> getTransactionsByAccount(String accountNumber) {
        return transactionDao.getTransactionsByAccount(accountNumber);
    }

    public void insertTransaction(Transaction transaction) {
        executorService.execute(() -> transactionDao.insertTransaction(transaction));
    }

    public void deleteTransaction(Transaction transaction) {
        executorService.execute(() -> transactionDao.deleteTransaction(transaction));
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executorService.shutdown();
    }
} 