package com.example.expensemate.data;

import androidx.lifecycle.LiveData;
import androidx.room.*;
import java.util.List;

@Dao
public interface RecurringPaymentDao {
    @Query("SELECT * FROM recurring_payments ORDER BY isCompleted ASC, dueDay ASC")
    LiveData<List<RecurringPayment>> getAllRecurringPayments();

    @Query("SELECT SUM(amount) FROM recurring_payments")
    LiveData<Double> getTotalAmount();

    @Query("SELECT SUM(amount) FROM recurring_payments WHERE isCompleted = 0")
    LiveData<Double> getRemainingAmount();

    @Insert
    void insert(RecurringPayment payment);

    @Update
    void update(RecurringPayment payment);

    @Delete
    void delete(RecurringPayment payment);

    @Query("SELECT * FROM recurring_payments WHERE id = :id")
    LiveData<RecurringPayment> getRecurringPayment(long id);
} 