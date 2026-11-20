package com.example.expensemate.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

/** What money is for. Accounts, where it is, are in {@link AccountDao}. */
@Dao
public interface FundDao {
    @Query("SELECT * FROM funds ORDER BY sortOrder ASC, name ASC")
    LiveData<List<Fund>> getAllFunds();

    @Query("SELECT * FROM funds ORDER BY sortOrder ASC, name ASC")
    List<Fund> getAllFundsSync();

    @Query("SELECT * FROM funds WHERE isDefault = 1 LIMIT 1")
    LiveData<Fund> getDefaultFund();

    @Query("SELECT * FROM funds WHERE isDefault = 1 LIMIT 1")
    Fund getDefaultFundSync();

    @Query("SELECT * FROM funds WHERE id = :id")
    Fund getFundByIdSync(long id);

    @Query("UPDATE funds SET isDefault = 0 WHERE isDefault = 1")
    void clearDefaultFund();

    @Query("UPDATE funds SET isDefault = 1 WHERE id = :fundId")
    void setDefaultFund(long fundId);

    @Insert
    long insert(Fund fund);

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM funds")
    int lastSortOrder();

    @Query("UPDATE funds SET sortOrder = :sortOrder WHERE id = :id")
    void setSortOrder(long id, int sortOrder);

    @Update
    void update(Fund fund);

    @Delete
    void delete(Fund fund);
}
