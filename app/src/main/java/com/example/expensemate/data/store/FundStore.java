package com.example.expensemate.data.store;

import androidx.lifecycle.LiveData;

import com.example.expensemate.data.Fund;

import java.util.List;

/** What money is for. Accounts, where it is, are in {@link AccountStore}. */
public interface FundStore {
    LiveData<List<Fund>> observeAll();

    /** Everyday money. */
    LiveData<Fund> observeDefault();

    Fund findDefault();

    List<Fund> findAll();

    Fund findById(long id);

    /** @return the new row's id */
    long insert(Fund fund);

    void update(Fund fund);

    void delete(Fund fund);

    int lastSortOrder();

    void setSortOrder(long id, int sortOrder);

    void clearDefault();

    void setDefault(long id);
}
