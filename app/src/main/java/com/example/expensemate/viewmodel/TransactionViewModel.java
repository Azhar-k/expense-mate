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
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.ArrayList;
import java.text.ParseException;

public class TransactionViewModel extends AndroidViewModel {
    private static final String TAG = "TransactionViewModel";
    private final AppDatabase database;
    private final TransactionDao transactionDao;
    private final ExecutorService executorService;
    private final MutableLiveData<String> selectedMonth = new MutableLiveData<>();
    private final MutableLiveData<String> selectedYear = new MutableLiveData<>();
    private final MutableLiveData<Double> totalExpense = new MutableLiveData<>(0.0);
    private final MutableLiveData<Double> totalIncome = new MutableLiveData<>(0.0);
    private final MutableLiveData<List<Transaction>> filteredTransactions = new MutableLiveData<>();
    private final AccountViewModel accountViewModel;
    private final MutableLiveData<Long> selectedAccountId = new MutableLiveData<>();

    // Filter fields
    private String description;
    private String receiver;
    private String category;
    private Double amount;
    private String transactionType;
    private boolean excludeFromSummary;
    private Long linkedRecurringPaymentId;
    private String fromDate;
    private String toDate;

    public TransactionViewModel(Application application) {
        super(application);
        Log.d(TAG, "Initializing TransactionViewModel");
        database = AppDatabase.getDatabase(application);
        transactionDao = database.transactionDao();
        executorService = Executors.newSingleThreadExecutor();
        accountViewModel = new AccountViewModel(application);
        
        // Initialize with current month and year for expense/summary screens
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat monthFormat = new SimpleDateFormat("MM", Locale.getDefault());
        SimpleDateFormat yearFormat = new SimpleDateFormat("yyyy", Locale.getDefault());
        String currentMonth = monthFormat.format(calendar.getTime());
        String currentYear = yearFormat.format(calendar.getTime());
        Log.d(TAG, "Setting initial period to: " + currentMonth + "/" + currentYear);
        selectedMonth.setValue(currentMonth);
        selectedYear.setValue(currentYear);
        
        // Initialize with default account (null for "All")
        selectedAccountId.setValue(null);
        
        // Initialize with default date range (last 30 days) for transactions screen
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        toDate = dateFormat.format(calendar.getTime());
        
        calendar.add(Calendar.DAY_OF_MONTH, -30);
        fromDate = dateFormat.format(calendar.getTime());
        
        // Initialize filtered transactions with default filters
        applyFilters();
        
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
                Double expense = transactionDao.getExpenseForExpenseScreen(month, year, accountId);
                Log.d(TAG, "Fetched expense total: " + expense);
                totalExpense.postValue(expense);
            });
            
            // Update income total
            executorService.execute(() -> {
                Double income = transactionDao.getIncomeForExpenseScreen(month, year, accountId);
                Log.d(TAG, "Fetched income total: " + income);
                totalIncome.postValue(income);
            });
            Log.d(TAG, "Updated category sums LiveData");
        } else {
            Log.w(TAG, "Cannot update LiveData: month or year is null");
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

    public LiveData<Double> getTotalExpense() {
        return totalExpense;
    }

    public LiveData<Double> getTotalIncome() {
        return totalIncome;
    }

    public double getTotalIncomeForAccount(long accountId) {
        return transactionDao.getTotalIncomeForAccountDetailsScreen(accountId);
    }

    public double getTotalExpenseForAccount(long accountId) {
        return transactionDao.getTotalExpenseForAccountDetailsScreen(accountId);
    }

    public LiveData<List<Transaction>> getTransactionsByDateRangeAndAccount(Date startDate, Date endDate, long accountId) {
        return transactionDao.getTransactionsForAccountDetailsScreen(startDate, endDate, accountId);
    }

    public LiveData<List<CategorySum>> getCategorySumsByMonthYearAndAccount(String month, String year, Long accountId) {
        return transactionDao.getExpenseCategorySumForSummaryScreen(month, year, accountId);
    }

    public LiveData<List<CategorySum>> getIncomeCategorySumsByMonthYearAndAccount(String month, String year, Long accountId) {
        return transactionDao.getIncomeCategorySumForSummaryScreen(month, year, accountId);
    }

    public LiveData<List<Transaction>> getTransactionsByCategoryAndPeriod(String category, String month, String year, Long accountId, String transactionType) {
        return transactionDao.getTransactionsByCategoryForSummaryScreen(category, month, year, accountId, transactionType);
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
                updatePeriodLiveData();
                applyFilters();
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
                updatePeriodLiveData();
                applyFilters();
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
                    newTransaction.getAccountId(),
                    newTransaction.isExcludedFromSummary()
                );
                updatePeriodLiveData();
                applyFilters();
            } catch (Exception e) {
                Log.e(TAG, "Error updating transaction", e);
            }
        });
    }

    public LiveData<List<Transaction>> getFilteredTransactions() {
        return filteredTransactions;
    }

    public void setSelectedAccount(Long accountId) {
        Log.d(TAG, "Setting selected account: " + accountId);
        selectedAccountId.setValue(accountId);
        updatePeriodLiveData();
        applyFilters();
    }

    public LiveData<Long> getSelectedAccountId() {
        return selectedAccountId;
    }

    public LiveData<List<Transaction>> getAllTransactionsSyncOrderByDateAsc() {
        MutableLiveData<List<Transaction>> result = new MutableLiveData<>();
        executorService.execute(() -> {
            List<Transaction> transactions = transactionDao.getAllTransactionsSyncOrderByDateAsc();
            result.postValue(transactions);
        });
        return result;
    }

    public String getCurrentFromDate() {
        return fromDate;
    }

    public String getCurrentToDate() {
        return toDate;
    }

    public void setFilters(String description, String receiver, String category, Double amount,
                          String transactionType, boolean excludeFromSummary, Long linkedRecurringPaymentId,
                          String fromDate, String toDate) {
        this.description = description;
        this.receiver = receiver;
        this.category = category;
        this.amount = amount;
        this.transactionType = transactionType;
        this.excludeFromSummary = excludeFromSummary;
        this.linkedRecurringPaymentId = linkedRecurringPaymentId;
        this.fromDate = fromDate;
        this.toDate = toDate;
        applyFilters();
    }

    public void clearFilters() {
        this.description = null;
        this.receiver = null;
        this.category = null;
        this.amount = null;
        this.transactionType = null;
        this.excludeFromSummary = false;
        this.linkedRecurringPaymentId = null;
        
        // Reset to default date range (last 30 days)
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        toDate = dateFormat.format(calendar.getTime());
        
        calendar.add(Calendar.DAY_OF_MONTH, -30);
        fromDate = dateFormat.format(calendar.getTime());
        
        applyFilters();
    }

    private void applyFilters() {
        executorService.execute(() -> {
            try {
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                Date from = dateFormat.parse(fromDate);
                Date to = dateFormat.parse(toDate);
                
                // Set time to start of day for from date
                Calendar fromCal = Calendar.getInstance();
                fromCal.setTime(from);
                fromCal.set(Calendar.HOUR_OF_DAY, 0);
                fromCal.set(Calendar.MINUTE, 0);
                fromCal.set(Calendar.SECOND, 0);
                fromCal.set(Calendar.MILLISECOND, 0);
                
                // Set time to end of day for to date
                Calendar toCal = Calendar.getInstance();
                toCal.setTime(to);
                toCal.set(Calendar.HOUR_OF_DAY, 23);
                toCal.set(Calendar.MINUTE, 59);
                toCal.set(Calendar.SECOND, 59);
                toCal.set(Calendar.MILLISECOND, 999);
                
                List<Transaction> transactions = transactionDao.getFilteredTransactions(
                    fromCal.getTime(),
                    toCal.getTime(),
                    selectedAccountId.getValue(),
                    description,
                    receiver,
                    category,
                    amount,
                    transactionType,
                    excludeFromSummary,
                    linkedRecurringPaymentId
                );
                
                filteredTransactions.postValue(transactions);
            } catch (ParseException e) {
                Log.e(TAG, "Error parsing dates", e);
            }
        });
    }

    public int countTransactionsBySmsHash(String smsHash) {
        return transactionDao.countTransactionsBySmsHash(smsHash);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executorService.shutdown();
    }
} 