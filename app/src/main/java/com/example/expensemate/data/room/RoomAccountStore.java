package com.example.expensemate.data.room;

import androidx.lifecycle.LiveData;

import com.example.expensemate.data.Account;
import com.example.expensemate.data.AccountDao;
import com.example.expensemate.data.store.AccountStore;

import java.util.List;

final class RoomAccountStore implements AccountStore {
    private final AccountDao dao;

    RoomAccountStore(AccountDao dao) {
        this.dao = dao;
    }

    @Override
    public LiveData<List<Account>> observeAll() {
        return dao.getAllAccounts();
    }

    @Override
    public LiveData<Account> observeDefault() {
        return dao.getDefaultAccount();
    }

    @Override
    public Account findDefault(String type) {
        return Account.TYPE_REAL.equals(type) ? dao.getDefaultRealAccountSync() : dao.getDefaultAccountSync();
    }

    @Override
    public void insert(Account account) {
        dao.insert(account);
    }

    @Override
    public void update(Account account) {
        dao.update(account);
    }

    @Override
    public void delete(Account account) {
        dao.delete(account);
    }

    @Override
    public int lastSortOrder(String type) {
        return dao.lastSortOrder(type);
    }

    @Override
    public void setSortOrder(long id, int sortOrder) {
        dao.setSortOrder(id, sortOrder);
    }

    @Override
    public void clearDefault(String type) {
        dao.clearDefaultAccount(type);
    }

    @Override
    public void setDefault(long id) {
        dao.setDefaultAccount(id);
    }
}
