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

    @Query("SELECT * FROM recurring_payments WHERE id = :id")
    RecurringPayment getByIdSync(long id);

    @Query("SELECT * FROM recurring_payments ORDER BY dueDay ASC")
    List<RecurringPayment> getAllRecurringPaymentsSync();

    @Insert
    void insert(RecurringPayment payment);

    @Update
    void update(RecurringPayment payment);

    @Delete
    void delete(RecurringPayment payment);

    // A deleted fund's top-ups go to another fund, so a bill never points at a fund that is gone
    @Query("UPDATE recurring_payments SET fromFundId = :toFundId WHERE fromFundId = :fromFundId")
    void moveFromFund(long fromFundId, long toFundId);

    @Query("UPDATE recurring_payments SET toFundId = :toFundId WHERE toFundId = :fromFundId")
    void moveToFund(long fromFundId, long toFundId);
} 