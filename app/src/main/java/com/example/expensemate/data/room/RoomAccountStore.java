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
    public Account findDefault() {
        return dao.getDefaultAccountSync();
    }

    @Override
    public Account findById(long id) {
        return dao.getAccountByIdSync(id);
    }

    @Override
    public List<Account> findAll() {
        return dao.getAllAccountsSync();
    }

    @Override
    public long insert(Account account) {
        return dao.insert(account);
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
    public int lastSortOrder() {
        return dao.lastSortOrder();
    }

    @Override
    public void setSortOrder(long id, int sortOrder) {
        dao.setSortOrder(id, sortOrder);
    }

    @Override
    public void clearDefault() {
        dao.clearDefaultAccount();
    }

    @Override
    public void setDefault(long id) {
        dao.setDefaultAccount(id);
    }
}
