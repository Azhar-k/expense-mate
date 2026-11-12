package com.example.expensemate.viewmodel;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import com.example.expensemate.AppServices;
import com.example.expensemate.data.Account;
import com.example.expensemate.domain.AccountService;
import com.example.expensemate.util.Currencies;
import java.util.List;

public class AccountViewModel extends AndroidViewModel {
    private static final String TAG = "AccountViewModel";
    private final AccountService accounts;

    public AccountViewModel(Application application) {
        super(application);
        accounts = AppServices.get(application).accounts();
    }

    public LiveData<List<Account>> getAllAccounts() {
        return accounts.accounts();
    }

    /** The default real account's currency, which lists and totals put first. */
    public LiveData<String> getMainCurrency() {
        return Transformations.distinctUntilChanged(Transformations.map(accounts.accounts(), Currencies::main));
    }

    public LiveData<Account> getDefaultAccount() {
        return accounts.defaultAccount();
    }

    public void setDefaultAccount(Account account) {
        Writes.log(TAG, "changing default account", accounts.makeDefault(account));
    }

    public void insert(Account account) {
        Writes.log(TAG, "adding account", accounts.add(account));
    }

    public void update(Account account) {
        Writes.log(TAG, "updating account", accounts.update(account));
    }

    public void saveOrder(List<Long> ids) {
        Writes.log(TAG, "reordering accounts", accounts.reorder(ids));
    }

    public void delete(Account account) {
        Writes.log(TAG, "deleting account", accounts.delete(account));
    }
}
