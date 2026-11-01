package com.example.expensemate.repository;

import android.app.Application;
import androidx.lifecycle.LiveData;
import com.example.expensemate.data.Account;
import com.example.expensemate.data.AppDatabase;
import com.example.expensemate.data.AccountDao;
import com.example.expensemate.data.TransactionDao;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AccountRepository {
    private AppDatabase db;
    private AccountDao accountDao;
    private TransactionDao transactionDao;
    private LiveData<List<Account>> allAccounts;
    private LiveData<Account> defaultAccount;
    private ExecutorService executorService;

    public AccountRepository(Application application) {
        db = AppDatabase.getDatabase(application);
        accountDao = db.accountDao();
        transactionDao = db.transactionDao();
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

    public void setDefaultAccount(Account account) {
        executorService.execute(() -> accountDao.makeDefault(account));
    }

    public void insert(Account account) {
        executorService.execute(() -> accountDao.insert(account));
    }

    public void update(Account account) {
        executorService.execute(() -> accountDao.update(account));
    }

    /** A transaction always keeps both accounts, so the deleted account's rows go to the default of its type. */
    public void delete(Account account) {
        executorService.execute(() -> {
            if (!account.isDefault()) {
                db.runInTransaction(() -> {
                    if (account.isReal()) {
                        transactionDao.moveToRealAccount(account.getId(), accountDao.getDefaultRealAccountSync().getId());
                    } else {
                        transactionDao.moveToAccount(account.getId(), accountDao.getDefaultAccountSync().getId());
                    }
                    accountDao.delete(account);
                });
            }
        });
    }

    public Account getDefaultAccountSync() {
        return accountDao.getDefaultAccountSync();
    }

    public Account getDefaultRealAccountSync() {
        return accountDao.getDefaultRealAccountSync();
    }
}
