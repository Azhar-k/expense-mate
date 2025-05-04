package com.example.expensemate.viewmodel;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import com.example.expensemate.data.RecurringPayment;
import com.example.expensemate.repository.RecurringPaymentRepository;
import java.util.List;
import java.util.Date;

public class RecurringPaymentsViewModel extends AndroidViewModel {
    private RecurringPaymentRepository repository;
    private LiveData<List<RecurringPayment>> allRecurringPayments;

    public RecurringPaymentsViewModel(Application application) {
        super(application);
        repository = new RecurringPaymentRepository(application);
        allRecurringPayments = repository.getAllRecurringPayments();
    }

    public LiveData<List<RecurringPayment>> getRecurringPayments() {
        return allRecurringPayments;
    }

    public void insert(RecurringPayment payment) {
        repository.insert(payment);
    }

    public void update(RecurringPayment payment) {
        repository.update(payment);
    }

    public void delete(RecurringPayment payment) {
        repository.delete(payment);
    }

    public void markAsCompleted(RecurringPayment payment) {
        payment.setCompleted(true);
        payment.setLastCompletedDate(new Date());
        repository.update(payment);
    }

    public void resetCompletionStatus(RecurringPayment payment) {
        payment.setCompleted(false);
        repository.update(payment);
    }
} 