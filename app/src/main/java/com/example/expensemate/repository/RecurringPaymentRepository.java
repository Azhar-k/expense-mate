package com.example.expensemate.repository;

import android.app.Application;
import androidx.lifecycle.LiveData;
import com.example.expensemate.data.RecurringPayment;
import com.example.expensemate.data.AppDatabase;
import com.example.expensemate.data.RecurringPaymentDao;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RecurringPaymentRepository {
    private RecurringPaymentDao recurringPaymentDao;
    private LiveData<List<RecurringPayment>> allRecurringPayments;
    private ExecutorService executorService;

    public RecurringPaymentRepository(Application application) {
        AppDatabase db = AppDatabase.getDatabase(application);
        recurringPaymentDao = db.recurringPaymentDao();
        allRecurringPayments = recurringPaymentDao.getAllRecurringPayments();
        executorService = Executors.newSingleThreadExecutor();
    }

    public LiveData<List<RecurringPayment>> getAllRecurringPayments() {
        return allRecurringPayments;
    }

    public LiveData<Double> getTotalAmount() {
        return recurringPaymentDao.getTotalAmount();
    }

    public LiveData<Double> getRemainingAmount() {
        return recurringPaymentDao.getRemainingAmount();
    }

    public void insert(RecurringPayment payment) {
        executorService.execute(() -> recurringPaymentDao.insert(payment));
    }

    public void update(RecurringPayment payment) {
        executorService.execute(() -> recurringPaymentDao.update(payment));
    }

    public void delete(RecurringPayment payment) {
        executorService.execute(() -> recurringPaymentDao.delete(payment));
    }
} 