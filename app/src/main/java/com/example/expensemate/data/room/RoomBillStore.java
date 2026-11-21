package com.example.expensemate.data.room;

import androidx.lifecycle.LiveData;

import com.example.expensemate.data.BillPayment;
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
        dao.deletePayments(bill.getId());
        dao.delete(bill);
    }

    @Override
    public LiveData<List<BillPayment>> observePayments() {
        return dao.getAllPayments();
    }

    @Override
    public List<BillPayment> findAllPayments() {
        return dao.getAllPaymentsSync();
    }

    @Override
    public List<BillPayment> findPayments(long billId) {
        return dao.getPaymentsSync(billId);
    }

    @Override
    public void savePayment(BillPayment payment) {
        dao.insertPayment(payment);
    }

    @Override
    public void deletePayment(long billId, int period) {
        dao.deletePayment(billId, period);
    }

    @Override
    public void deletePaymentsOf(long transactionId) {
        dao.deletePaymentsOf(transactionId);
    }

    @Override
    public void moveFund(long fromFundId, long toFundId) {
        dao.moveFromFund(fromFundId, toFundId);
        dao.moveToFund(fromFundId, toFundId);
    }
}
