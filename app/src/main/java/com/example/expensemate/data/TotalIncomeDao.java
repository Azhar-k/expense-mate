package com.example.expensemate.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface TotalIncomeDao {
    @Query("SELECT * FROM total_income WHERE id = 1")
    LiveData<TotalIncome> getTotalIncome();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(TotalIncome totalIncome);

    @Query("UPDATE total_income SET amount = amount + :amount WHERE id = 1")
    void incrementAmount(double amount);

    @Query("UPDATE total_income SET amount = amount - :amount WHERE id = 1")
    void decrementAmount(double amount);

    @Query("SELECT amount FROM total_income WHERE id = 1")
    double getCurrentTotal();
} 