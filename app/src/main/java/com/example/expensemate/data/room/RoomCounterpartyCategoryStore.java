package com.example.expensemate.data.room;

import com.example.expensemate.data.TransactionDao;
import com.example.expensemate.data.store.CounterpartyCategoryStore;

final class RoomCounterpartyCategoryStore implements CounterpartyCategoryStore {
    private final TransactionDao dao;

    RoomCounterpartyCategoryStore(TransactionDao dao) {
        this.dao = dao;
    }

    @Override
    public String find(String counterparty, String transactionType) {
        return dao.getCounterpartyCategory(counterparty, transactionType);
    }

    @Override
    public void save(String counterparty, String transactionType, String category) {
        dao.saveCounterpartyCategory(counterparty, transactionType, category);
    }

    @Override
    public void delete(String counterparty, String transactionType) {
        dao.deleteCounterpartyCategory(counterparty, transactionType);
    }
}
