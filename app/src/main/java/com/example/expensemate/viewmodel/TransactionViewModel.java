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
import com.example.expensemate.data.Account;
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
    private final MutableLiveData<String> selectedMonth = new MutableLiveData<>();
    private final MutableLiveData<String> selectedYear = new MutableLiveData<>();
    private final MutableLiveData<Double> totalExpense = new MutableLiveData<>(0.0);
    private final MutableLiveData<Double> totalIncome = new MutableLiveData<>(0.0);
    private LiveData<List<CategorySum>> categorySums;
    private final MutableLiveData<List<Transaction>> filteredTransactions = new MutableLiveData<>();
    private final AccountViewModel accountViewModel;
    private final MutableLiveData<Long> selectedAccountId = new MutableLiveData<>();

    public TransactionViewModel(Application application) {
        super(application);
        Log.d(TAG, "Initializing TransactionViewModel");
        database = AppDatabase.getDatabase(application);
        transactionDao = database.transactionDao();
        executorService = Executors.newSingleThreadExecutor();
        accountViewModel = new AccountViewModel(application);
        
        // Initialize with current month and year
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat monthFormat = new SimpleDateFormat("MM", Locale.getDefault());
        SimpleDateFormat yearFormat = new SimpleDateFormat("yyyy", Locale.getDefault());
        String currentMonth = monthFormat.format(calendar.getTime());
        String currentYear = yearFormat.format(calendar.getTime());
        Log.d(TAG, "Setting initial period to: " + currentMonth + "/" + currentYear);
        selectedMonth.setValue(currentMonth);
        selectedYear.setValue(currentYear);
        
        // Initialize with default account
        accountViewModel.getDefaultAccount().observeForever(account -> {
            if (account != null) {
                selectedAccountId.setValue(account.getId());
            }
        });
        
        // Initialize filtered transactions with current month/year and default account
        executorService.execute(() -> {
            List<Transaction> transactions = transactionDao.getTransactionsByMonthYearAndAccountSync(
                currentMonth, 
                currentYear,
                selectedAccountId.getValue()
            );
            Log.d(TAG, "Initialized filtered transactions with count: " + (transactions != null ? transactions.size() : 0));
            filteredTransactions.postValue(transactions);
        });
        
        // Initialize LiveData with current month/year
        updatePeriodLiveData();
    }

    private void updatePeriodLiveData() {
        String month = selectedMonth.getValue();
        String year = selectedYear.getValue();
        Long accountId = selectedAccountId.getValue();
        
        if (month != null && year != null) {
            Log.d(TAG, "Updating LiveData for period: " + month + "/" + year);
            
            // Update expense total
            executorService.execute(() -> {
                Double expense = transactionDao.getExpenseByMonthYearAndAccountSync(month, year, accountId);
                Log.d(TAG, "Fetched expense total: " + expense);
                totalExpense.postValue(expense);
            });
            
            // Update income total
            executorService.execute(() -> {
                Double income = transactionDao.getIncomeByMonthYearAndAccountSync(month, year, accountId);
                Log.d(TAG, "Fetched income total: " + income);
                totalIncome.postValue(income);
            });
            
            // Update category sums
            categorySums = transactionDao.getCategorySumsByMonthYearAndAccount(month, year, accountId);
            Log.d(TAG, "Updated category sums LiveData");
        } else {
            Log.w(TAG, "Cannot update LiveData: month or year is null");
        }
    }

    public void setSelectedMonthYear(String month, String year) {
        Log.d(TAG, "Setting new period: " + month + "/" + year);
        selectedMonth.setValue(month);
        selectedYear.setValue(year);
        
        // Update all data for the new period
        executorService.execute(() -> {
            // Update filtered transactions
            List<Transaction> transactions = transactionDao.getTransactionsByMonthYearAndAccountSync(
                month, 
                year,
                selectedAccountId.getValue()
            );
            filteredTransactions.postValue(transactions);
            
            // Update expense total
            Double expense = transactionDao.getExpenseByMonthYearAndAccountSync(month, year, selectedAccountId.getValue());
            totalExpense.postValue(expense);
            
            // Update income total
            Double income = transactionDao.getIncomeByMonthYearAndAccountSync(month, year, selectedAccountId.getValue());
            totalIncome.postValue(income);
            
            // Category sums will be updated automatically through LiveData
        });
    }

    public LiveData<String> getSelectedMonth() {
        return selectedMonth;
    }

    public LiveData<String> getSelectedYear() {
        return selectedYear;
    }

    public LiveData<List<Transaction>> getFilteredTransactions() {
        Log.d(TAG, "Getting filtered transactions LiveData");
        return filteredTransactions;
    }

    public void insertTransaction(Transaction transaction) {
        executorService.execute(() -> {
            try {
                Log.d(TAG, "Inserting transaction: " + transaction.getAmount() + " " + transaction.getTransactionType());
                // If no account is set, use the default account
                if (transaction.getAccountId() == null) {
                    Account defaultAccount = accountViewModel.getDefaultAccount().getValue();
                    if (defaultAccount != null) {
                        transaction.setAccountId(defaultAccount.getId());
                    }
                }
                transactionDao.insertTransaction(transaction);
                // Update the current period totals and filtered transactions
                String month = selectedMonth.getValue();
                String year = selectedYear.getValue();
                if (month != null && year != null) {
                    List<Transaction> transactions = transactionDao.getTransactionsByMonthYearSync(month, year);
                    filteredTransactions.postValue(transactions);
                }
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
                // Update the current period totals and filtered transactions
                String month = selectedMonth.getValue();
                String year = selectedYear.getValue();
                if (month != null && year != null) {
                    List<Transaction> transactions = transactionDao.getTransactionsByMonthYearSync(month, year);
                    filteredTransactions.postValue(transactions);
                }
                updatePeriodLiveData();
            } catch (Exception e) {
                Log.e(TAG, "Error deleting transaction", e);
            }
        });
    }

    public void updateTransaction(Transaction oldTransaction, Transaction newTransaction) {
        executorService.execute(() -> {
            try {
                Log.d(TAG, "Updating transaction: " + newTransaction.getAmount() + " " + newTransaction.getTransactionType());
                transactionDao.updateTransaction(
                    newTransaction.getId(),
                    newTransaction.getAmount(),
                    newTransaction.getDescription(),
                    newTransaction.getDate(),
                    newTransaction.getTransactionType(),
                    newTransaction.getReceiverName(),
                    newTransaction.getSmsBody(),
                    newTransaction.getSmsSender(),
                    newTransaction.getCategory(),
                    newTransaction.getLinkedRecurringPaymentId(),
                    newTransaction.getAccountId()
                );
                // Update the current period totals and filtered transactions
                String month = selectedMonth.getValue();
                String year = selectedYear.getValue();
                if (month != null && year != null) {
                    List<Transaction> transactions = transactionDao.getTransactionsByMonthYearSync(month, year);
                    filteredTransactions.postValue(transactions);
                }
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

    public LiveData<List<CategorySum>> getCategorySumsByMonthYear(String month, String year) {
        return transactionDao.getCategorySumsByMonthYear(month, year);
    }

    public LiveData<List<CategorySum>> getCategorySumsByMonthYearAndAccount(String month, String year, Long accountId) {
        return transactionDao.getCategorySumsByMonthYearAndAccount(month, year, accountId);
    }

    public int countTransactionsBySmsHash(String smsHash) {
        return transactionDao.countTransactionsBySmsHash(smsHash);
    }

    public void setSelectedAccount(Long accountId) {
        selectedAccountId.setValue(accountId);
        updateFilteredTransactions();
    }

    public LiveData<Long> getSelectedAccountId() {
        return selectedAccountId;
    }

    private void updateFilteredTransactions() {
        String month = selectedMonth.getValue();
        String year = selectedYear.getValue();
        Long accountId = selectedAccountId.getValue();
        
        if (month != null && year != null) {
            executorService.execute(() -> {
                List<Transaction> transactions = transactionDao.getTransactionsByMonthYearAndAccountSync(
                    month, 
                    year,
                    accountId
                );
                filteredTransactions.postValue(transactions);
            });
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executorService.shutdown();
    }
} 