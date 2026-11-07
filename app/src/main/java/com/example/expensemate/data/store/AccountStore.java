package com.example.expensemate.data.store;

import androidx.lifecycle.LiveData;

import com.example.expensemate.data.Account;

import java.util.List;

public interface AccountStore {
    LiveData<List<Account>> observeAll();

    LiveData<Account> observeDefault();

    /** @param type Account.TYPE_VIRTUAL or Account.TYPE_REAL */
    Account findDefault(String type);

    List<Account> findAll();

    void insert(Account account);

    void update(Account account);

    void delete(Account account);

    int lastSortOrder(String type);

    void setSortOrder(long id, int sortOrder);

    void clearDefault(String type);

    void setDefault(long id);
}
