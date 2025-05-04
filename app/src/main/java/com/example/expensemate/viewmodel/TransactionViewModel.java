package com.example.expensemate.viewmodel;

import android.app.Application;
import android.util.Log;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.example.expensemate.data.AppDatabase;
import com.example.expensemate.data.Transaction;
import com.example.expensemate.data.TransactionDao;
import com.example.expensemate.data.CategorySum;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TransactionViewModel extends AndroidViewModel {
    private static final String TAG = "TransactionViewModel";
    private final AppDatabase database;
    private final TransactionDao transactionDao;
    private final ExecutorService executorService;
    private final LiveData<List<Transaction>> allTransactions;
    private final MutableLiveData<String> selectedMonth = new MutableLiveData<>();
    private final MutableLiveData<String> selectedYear = new MutableLiveData<>();
    private final MutableLiveData<Double> totalExpense = new MutableLiveData<>(0.0);
    private final MutableLiveData<Double> totalIncome = new MutableLiveData<>(0.0);
    private LiveData<List<CategorySum>> categorySums;

    public TransactionViewModel(Application application) {
        super(application);
        database = AppDatabase.getDatabase(application);
        transactionDao = database.transactionDao();
        executorService = Executors.newSingleThreadExecutor();
        allTransactions = transactionDao.getAllTransactions();
        
        // Initialize with current month and year
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat monthFormat = new SimpleDateFormat("MM", Locale.getDefault());
        SimpleDateFormat yearFormat = new SimpleDateFormat("yyyy", Locale.getDefault());
        selectedMonth.setValue(monthFormat.format(calendar.getTime()));
        selectedYear.setValue(yearFormat.format(calendar.getTime()));
        
        // Initialize LiveData with current month/year
        updatePeriodLiveData();
    }

    private void updatePeriodLiveData() {
        String month = selectedMonth.getValue();
        String year = selectedYear.getValue();
        if (month != null && year != null) {
            Log.d(TAG, "Updating LiveData for period: " + month + "/" + year);
            
            // Update expense total
            executorService.execute(() -> {
                Double expense = transactionDao.getExpenseByMonthYearSync(month, year);
                Log.d(TAG, "Fetched expense total: " + expense);
                totalExpense.postValue(expense);
            });
            
            // Update income total
            executorService.execute(() -> {
                Double income = transactionDao.getIncomeByMonthYearSync(month, year);
                Log.d(TAG, "Fetched income total: " + income);
                totalIncome.postValue(income);
            });
            
            // Update category sums
            categorySums = transactionDao.getCategorySumsByMonthYear(month, year);
        }
    }

    public void setSelectedMonthYear(String month, String year) {
        Log.d(TAG, "Setting new period: " + month + "/" + year);
        selectedMonth.setValue(month);
        selectedYear.setValue(year);
        updatePeriodLiveData();
    }

    public LiveData<String> getSelectedMonth() {
        return selectedMonth;
    }

    public LiveData<String> getSelectedYear() {
        return selectedYear;
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
                // Update the current period totals
                updatePeriodLiveData();
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
                // Update the current period totals
                updatePeriodLiveData();
            } catch (Exception e) {
                Log.e(TAG, "Error deleting transaction", e);
            }
        });
    }

    public void updateTransaction(Transaction oldTransaction, Transaction newTransaction) {
        executorService.execute(() -> {
            try {
                Log.d(TAG, "Updating transaction: " + oldTransaction.getId());
                
                // Update the transaction
                transactionDao.updateTransaction(
                    newTransaction.getId(),
                    newTransaction.getAmount(),
                    newTransaction.getDescription(),
                    newTransaction.getDate(),
                    newTransaction.getAccountNumber(),
                    newTransaction.getAccountType(),
                    newTransaction.getTransactionType(),
                    newTransaction.getReceiverName(),
                    newTransaction.getSmsBody(),
                    newTransaction.getSmsSender(),
                    newTransaction.getCategory()
                );
                
                // Update the current period totals
                updatePeriodLiveData();
            } catch (Exception e) {
                Log.e(TAG, "Error updating transaction", e);
            }
        });
    }

    public LiveData<Double> getTotalExpense() {
        return totalExpense;
    }

    public LiveData<Double> getTotalIncome() {
        return totalIncome;
    }

    public LiveData<List<CategorySum>> getCategorySums() {
        return categorySums;
    }

    public LiveData<List<CategorySum>> getCategorySumsByMonthYear(String month, String year) {
        return transactionDao.getCategorySumsByMonthYear(month, year);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executorService.shutdown();
    }
} 