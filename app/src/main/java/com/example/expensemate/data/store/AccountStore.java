package com.example.expensemate.data.store;

import androidx.lifecycle.LiveData;

import com.example.expensemate.data.Account;

import java.util.List;

/** Where money is. Funds, what it is for, are in {@link FundStore}. */
public interface AccountStore {
    LiveData<List<Account>> observeAll();

    LiveData<Account> observeDefault();

    Account findDefault();

    List<Account> findAll();

    Account findById(long id);

    /** @return the new row's id */
    long insert(Account account);

    void update(Account account);

    void delete(Account account);

    int lastSortOrder();

    void setSortOrder(long id, int sortOrder);

    void clearDefault();

    void setDefault(long id);
}
