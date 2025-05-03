package com.example.expensemate.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

@Dao
public interface TotalExpenseDao {
    @Query("SELECT * FROM total_expense WHERE id = 1")
    LiveData<TotalExpense> getTotalExpense();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(TotalExpense totalExpense);

    @Update
    void update(TotalExpense totalExpense);

    @Query("UPDATE total_expense SET amount = amount + :amount WHERE id = 1")
    void incrementAmount(double amount);

    @Query("UPDATE total_expense SET amount = amount - :amount WHERE id = 1")
    void decrementAmount(double amount);
} 