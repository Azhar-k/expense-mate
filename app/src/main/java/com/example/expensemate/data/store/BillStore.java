package com.example.expensemate.data.store;

import androidx.lifecycle.LiveData;

import com.example.expensemate.data.BillPayment;
import com.example.expensemate.data.RecurringPayment;

import java.util.List;

public interface BillStore {
    LiveData<List<RecurringPayment>> observeAll();

    LiveData<Double> observeTotalAmount();

    LiveData<Double> observeRemainingAmount();

    RecurringPayment findById(long id);

    List<RecurringPayment> findAll();

    void insert(RecurringPayment bill);

    void update(RecurringPayment bill);

    void delete(RecurringPayment bill);

    LiveData<List<BillPayment>> observePayments();

    List<BillPayment> findAllPayments();

    List<BillPayment> findPayments(long billId);

    /** Replaces whatever that bill's month had. */
    void savePayment(BillPayment payment);

    void deletePayment(long billId, int period);

    /** Unmarks whichever months the transaction paid. */
    void deletePaymentsOf(long transactionId);

    /** Bills that top up from or into one fund point at another instead, for when that fund goes. */
    void moveFund(long fromFundId, long toFundId);
}
