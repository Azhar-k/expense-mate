package com.example.expensemate.viewmodel;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import com.example.expensemate.AppServices;
import com.example.expensemate.data.RecurringPayment;
import com.example.expensemate.domain.BillService;
import java.util.List;

public class RecurringPaymentsViewModel extends AndroidViewModel {
    private static final String TAG = "RecurringPaymentsViewModel";
    private final BillService bills;

    public RecurringPaymentsViewModel(Application application) {
        super(application);
        bills = AppServices.get(application).bills();
    }

    public LiveData<List<RecurringPayment>> getRecurringPayments() {
        return bills.bills();
    }

    public LiveData<Double> getTotalAmount() {
        return bills.totalAmount();
    }

    public LiveData<Double> getRemainingAmount() {
        return bills.remainingAmount();
    }

    public void insert(RecurringPayment payment) {
        Writes.log(TAG, "adding bill", bills.add(payment));
    }

    public void update(RecurringPayment payment) {
        Writes.log(TAG, "updating bill", bills.update(payment));
    }

    public void delete(RecurringPayment payment) {
        Writes.log(TAG, "deleting bill", bills.delete(payment));
    }

    public void markAsCompleted(RecurringPayment payment) {
        Writes.log(TAG, "marking bill paid", bills.markPaid(payment));
    }

    public void resetCompletionStatus(RecurringPayment payment) {
        Writes.log(TAG, "marking bill unpaid", bills.resetPaid(payment));
    }

    /** Moves the bill's amount between its accounts and marks it paid. */
    public void payByTransfer(RecurringPayment payment) {
        Writes.log(TAG, "moving bill amount", bills.payByTransfer(payment));
    }
}
