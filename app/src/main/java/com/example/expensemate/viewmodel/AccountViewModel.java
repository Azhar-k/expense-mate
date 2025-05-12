package com.example.expensemate.viewmodel;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import com.example.expensemate.data.Account;
import com.example.expensemate.repository.AccountRepository;
import java.util.List;

public class AccountViewModel extends AndroidViewModel {
    private AccountRepository repository;
    private LiveData<List<Account>> allAccounts;
    private LiveData<Account> defaultAccount;

    public AccountViewModel(Application application) {
        super(application);
        repository = new AccountRepository(application);
        allAccounts = repository.getAllAccounts();
        defaultAccount = repository.getDefaultAccount();
    }

    public LiveData<List<Account>> getAllAccounts() {
        return allAccounts;
    }

    public LiveData<Account> getDefaultAccount() {
        return defaultAccount;
    }

    public void setDefaultAccount(long accountId) {
        repository.setDefaultAccount(accountId);
    }

    public void insert(Account account) {
        repository.insert(account);
    }

    public void update(Account account) {
        repository.update(account);
    }

    public void delete(Account account) {
        repository.delete(account);
    }
} 