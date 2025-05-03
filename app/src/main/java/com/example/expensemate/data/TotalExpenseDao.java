package com.example.expensemate.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

@Dao
public interface TotalExpenseDao {
    @Query("SELECT * FROM total_expense WHERE id = 1")
    LiveData<TotalExpense> getTotalExpense();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(TotalExpense totalExpense);

    @Query("UPDATE total_expense SET amount = amount + :amount WHERE id = 1")
    void incrementAmount(double amount);

    @Query("UPDATE total_expense SET amount = amount - :amount WHERE id = 1")
    void decrementAmount(double amount);

    @Query("SELECT amount FROM total_expense WHERE id = 1")
    double getCurrentTotal();
} 