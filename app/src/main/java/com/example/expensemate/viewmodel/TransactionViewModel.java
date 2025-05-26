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
            List<Transaction> transactions = transactionDao.getTransactionsByAccountForTransactionScreen(
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
        
        // Update all data for the new period
        executorService.execute(() -> {
            // Update filtered transactions
            List<Transaction> transactions = transactionDao.getTransactionsByAccountForTransactionScreen(
                month, 
                year,
                selectedAccountId.getValue()
            );
            filteredTransactions.postValue(transactions);
            
            // Update expense total
            Double expense = transactionDao.getExpenseForExpenseScreen(month, year, selectedAccountId.getValue());
            totalExpense.postValue(expense);
            
            // Update income total
            Double income = transactionDao.getIncomeForExpenseScreen(month, year, selectedAccountId.getValue());
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
                    List<Transaction> transactions = transactionDao.getTransactionsForTransactionScreen(month, year);
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
                    List<Transaction> transactions = transactionDao.getTransactionsForTransactionScreen(month, year);
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
                    newTransaction.getAccountId(),
                    newTransaction.isExcludedFromSummary()
                );
                // Update the current period totals and filtered transactions
                String month = selectedMonth.getValue();
                String year = selectedYear.getValue();
                if (month != null && year != null) {
                    List<Transaction> transactions = transactionDao.getTransactionsForTransactionScreen(month, year);
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

    public int countTransactionsBySmsHash(String smsHash) {
        return transactionDao.countTransactionsBySmsHash(smsHash);
    }

    public void setSelectedAccount(Long accountId) {
        Log.d(TAG, "Setting selected account: " + accountId);
        selectedAccountId.setValue(accountId);
        
        // Update all data for the new account
        String month = selectedMonth.getValue();
        String year = selectedYear.getValue();
        
        if (month != null && year != null) {
            executorService.execute(() -> {
                // Update filtered transactions
                List<Transaction> transactions;
                if (accountId == null) {
                    // If accountId is null, get all transactions without account filter
                    transactions = transactionDao.getTransactionsForTransactionScreen(month, year);
                } else {
                    transactions = transactionDao.getTransactionsByAccountForTransactionScreen(
                        month, 
                        year,
                        accountId
                    );
                }
                filteredTransactions.postValue(transactions);
                
                // Update expense total
                Double expense = transactionDao.getExpenseForExpenseScreen(month, year, accountId);
                totalExpense.postValue(expense);
                
                // Update income total
                Double income = transactionDao.getIncomeForExpenseScreen(month, year, accountId);
                totalIncome.postValue(income);
                
                // Category sums will be updated automatically through LiveData
            });
        }
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
        this.fromDate = null;
        this.toDate = null;
        applyFilters();
    }

    private void applyFilters() {
        executorService.execute(() -> {
            List<Transaction> filteredList = new ArrayList<>();
            List<Transaction> allTransactions;

            // Get transactions based on account filter
            if (selectedAccountId.getValue() != null) {
                allTransactions = transactionDao.getTransactionsByAccountForTransactionScreen(
                    selectedMonth.getValue(),
                    selectedYear.getValue(),
                    selectedAccountId.getValue()
                );
            } else {
                allTransactions = transactionDao.getTransactionsForTransactionScreen(
                    selectedMonth.getValue(),
                    selectedYear.getValue()
                );
            }

            for (Transaction transaction : allTransactions) {
                if (matchesFilters(transaction)) {
                    filteredList.add(transaction);
                }
            }

            filteredTransactions.postValue(filteredList);
        });
    }

    private boolean matchesFilters(Transaction transaction) {
        // Check if transaction matches all active filters
        if (description != null && !transaction.getDescription().toLowerCase().contains(description.toLowerCase())) {
            return false;
        }
        if (receiver != null && !transaction.getReceiverName().toLowerCase().contains(receiver.toLowerCase())) {
            return false;
        }
        if (category != null && !transaction.getCategory().equals(category)) {
            return false;
        }
        if (amount != null && transaction.getAmount() != amount) {
            return false;
        }
        if (transactionType != null && !transaction.getTransactionType().equals(transactionType)) {
            return false;
        }
        if (excludeFromSummary && !transaction.isExcludedFromSummary()) {
            return false;
        }
        if (linkedRecurringPaymentId != null && 
            (transaction.getLinkedRecurringPaymentId() == null || 
             !transaction.getLinkedRecurringPaymentId().equals(linkedRecurringPaymentId))) {
            return false;
        }
        if (fromDate != null || toDate != null) {
            try {
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                Date transactionDate = transaction.getDate();
                
                if (fromDate != null) {
                    Date from = dateFormat.parse(fromDate);
                    Calendar fromCal = Calendar.getInstance();
                    fromCal.setTime(from);
                    fromCal.set(Calendar.HOUR_OF_DAY, 0);
                    fromCal.set(Calendar.MINUTE, 0);
                    fromCal.set(Calendar.SECOND, 0);
                    fromCal.set(Calendar.MILLISECOND, 0);
                    
                    if (transactionDate.before(fromCal.getTime())) {
                        return false;
                    }
                }
                
                if (toDate != null) {
                    Date to = dateFormat.parse(toDate);
                    Calendar toCal = Calendar.getInstance();
                    toCal.setTime(to);
                    toCal.set(Calendar.HOUR_OF_DAY, 23);
                    toCal.set(Calendar.MINUTE, 59);
                    toCal.set(Calendar.SECOND, 59);
                    toCal.set(Calendar.MILLISECOND, 999);
                    
                    if (transactionDate.after(toCal.getTime())) {
                        return false;
                    }
                }
            } catch (ParseException e) {
                e.printStackTrace();
                return false;
            }
        }
        return true;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executorService.shutdown();
    }
} 