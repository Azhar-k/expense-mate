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
    long insert(RecurringPayment payment);

    @Update
    void update(RecurringPayment payment);

    @Delete
    void delete(RecurringPayment payment);

    @Query("SELECT * FROM bill_payments")
    LiveData<List<BillPayment>> getAllPayments();

    @Query("SELECT * FROM bill_payments ORDER BY billId, period")
    List<BillPayment> getAllPaymentsSync();

    @Query("SELECT * FROM bill_payments WHERE billId = :billId")
    List<BillPayment> getPaymentsSync(long billId);

    @Query("SELECT * FROM bill_payments WHERE billId = :billId AND period = :period")
    BillPayment getPaymentSync(long billId, int period);

    // A month has one row, so paying it again replaces the earlier one
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertPayment(BillPayment payment);

    @Query("DELETE FROM bill_payments WHERE billId = :billId AND period = :period")
    void deletePayment(long billId, int period);

    @Query("DELETE FROM bill_payments WHERE billId = :billId")
    void deletePayments(long billId);

    @Query("DELETE FROM bill_payments WHERE transactionId = :transactionId")
    void deletePaymentsOf(long transactionId);

    // A deleted fund's top-ups go to another fund, so a bill never points at a fund that is gone
    @Query("UPDATE recurring_payments SET fromFundId = :toFundId WHERE fromFundId = :fromFundId")
    void moveFromFund(long fromFundId, long toFundId);

    @Query("UPDATE recurring_payments SET toFundId = :toFundId WHERE toFundId = :fromFundId")
    void moveToFund(long fromFundId, long toFundId);
} 