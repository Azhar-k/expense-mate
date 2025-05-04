package com.example.expensemate.data;

import androidx.lifecycle.LiveData;
import androidx.room.*;
import java.util.List;

@Dao
public interface RecurringPaymentDao {
    @Query("SELECT * FROM recurring_payments ORDER BY dueDay ASC")
    LiveData<List<RecurringPayment>> getAllRecurringPayments();

    @Insert
    void insert(RecurringPayment payment);

    @Update
    void update(RecurringPayment payment);

    @Delete
    void delete(RecurringPayment payment);

    @Query("SELECT * FROM recurring_payments WHERE id = :id")
    LiveData<RecurringPayment> getRecurringPayment(long id);
} 