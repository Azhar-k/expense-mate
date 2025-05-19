package com.example.expensemate.repository;

import android.app.Application;
import androidx.lifecycle.LiveData;
import com.example.expensemate.data.Account;
import com.example.expensemate.data.AppDatabase;
import com.example.expensemate.data.AccountDao;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AccountRepository {
    private AccountDao accountDao;
    private LiveData<List<Account>> allAccounts;
    private LiveData<Account> defaultAccount;
    private ExecutorService executorService;

    public AccountRepository(Application application) {
        AppDatabase db = AppDatabase.getDatabase(application);
        accountDao = db.accountDao();
        allAccounts = accountDao.getAllAccounts();
        defaultAccount = accountDao.getDefaultAccount();
        executorService = Executors.newSingleThreadExecutor();
    }

    public LiveData<List<Account>> getAllAccounts() {
        return allAccounts;
    }

    public LiveData<Account> getDefaultAccount() {
        return defaultAccount;
    }

    public void setDefaultAccount(long accountId) {
        executorService.execute(() -> {
            accountDao.clearDefaultAccount();
            accountDao.setDefaultAccount(accountId);
        });
    }

    public void insert(Account account) {
        executorService.execute(() -> accountDao.insert(account));
    }

    public void update(Account account) {
        executorService.execute(() -> accountDao.update(account));
    }

    public void delete(Account account) {
        executorService.execute(() -> {
            if (!account.isDefault()) {
                accountDao.delete(account);
            }
        });
    }

    public Account getDefaultAccountSync() {
        return accountDao.getDefaultAccountSync();
    }
}