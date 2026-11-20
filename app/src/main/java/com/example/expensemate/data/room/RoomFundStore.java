package com.example.expensemate.data.room;

import androidx.lifecycle.LiveData;

import com.example.expensemate.data.Fund;
import com.example.expensemate.data.FundDao;
import com.example.expensemate.data.store.FundStore;

import java.util.List;

final class RoomFundStore implements FundStore {
    private final FundDao dao;

    RoomFundStore(FundDao dao) {
        this.dao = dao;
    }

    @Override
    public LiveData<List<Fund>> observeAll() {
        return dao.getAllFunds();
    }

    @Override
    public LiveData<Fund> observeDefault() {
        return dao.getDefaultFund();
    }

    @Override
    public Fund findDefault() {
        return dao.getDefaultFundSync();
    }

    @Override
    public List<Fund> findAll() {
        return dao.getAllFundsSync();
    }

    @Override
    public Fund findById(long id) {
        return dao.getFundByIdSync(id);
    }

    @Override
    public long insert(Fund fund) {
        return dao.insert(fund);
    }

    @Override
    public void update(Fund fund) {
        dao.update(fund);
    }

    @Override
    public void delete(Fund fund) {
        dao.delete(fund);
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
        dao.clearDefaultFund();
    }

    @Override
    public void setDefault(long id) {
        dao.setDefaultFund(id);
    }
}
