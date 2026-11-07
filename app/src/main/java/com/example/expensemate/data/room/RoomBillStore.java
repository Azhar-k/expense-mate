package com.example.expensemate.data.room;

import androidx.lifecycle.LiveData;

import com.example.expensemate.data.RecurringPayment;
import com.example.expensemate.data.RecurringPaymentDao;
import com.example.expensemate.data.store.BillStore;

import java.util.List;

final class RoomBillStore implements BillStore {
    private final RecurringPaymentDao dao;

    RoomBillStore(RecurringPaymentDao dao) {
        this.dao = dao;
    }

    @Override
    public LiveData<List<RecurringPayment>> observeAll() {
        return dao.getAllRecurringPayments();
    }

    @Override
    public LiveData<Double> observeTotalAmount() {
        return dao.getTotalAmount();
    }

    @Override
    public LiveData<Double> observeRemainingAmount() {
        return dao.getRemainingAmount();
    }

    @Override
    public RecurringPayment findById(long id) {
        return dao.getByIdSync(id);
    }

    @Override
    public List<RecurringPayment> findAll() {
        return dao.getAllRecurringPaymentsSync();
    }

    @Override
    public void insert(RecurringPayment bill) {
        dao.insert(bill);
    }

    @Override
    public void update(RecurringPayment bill) {
        dao.update(bill);
    }

    @Override
    public void delete(RecurringPayment bill) {
        dao.delete(bill);
    }
}
