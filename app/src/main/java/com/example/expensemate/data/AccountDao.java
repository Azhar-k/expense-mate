package com.example.expensemate.data;

import androidx.lifecycle.LiveData;
import androidx.room.*;
import java.util.List;

@Dao
public interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY name ASC")
    LiveData<List<Account>> getAllAccounts();

    @Query("SELECT * FROM accounts ORDER BY name ASC")
    List<Account> getAllAccountsSync();

    @Query("SELECT * FROM accounts WHERE isDefault = 1 LIMIT 1")
    LiveData<Account> getDefaultAccount();

    @Query("UPDATE accounts SET isDefault = 0 WHERE isDefault = 1")
    void clearDefaultAccount();

    @Query("UPDATE accounts SET isDefault = 1 WHERE id = :accountId")
    void setDefaultAccount(long accountId);

    @Insert
    void insert(Account account);

    @Update
    void update(Account account);

    @Delete
    void delete(Account account);
} 