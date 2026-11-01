package com.example.expensemate.data;

import androidx.lifecycle.LiveData;
import androidx.room.*;
import java.util.List;

@Dao
public interface AccountDao {
    // Virtual accounts first, so every list reads the same way
    @Query("SELECT * FROM accounts ORDER BY type DESC, name ASC")
    LiveData<List<Account>> getAllAccounts();

    @Query("SELECT * FROM accounts ORDER BY type DESC, name ASC")
    List<Account> getAllAccountsSync();

    @Query("SELECT * FROM accounts WHERE isDefault = 1 AND type = 'VIRTUAL' LIMIT 1")
    LiveData<Account> getDefaultAccount();

    @Query("SELECT * FROM accounts WHERE isDefault = 1 AND type = 'VIRTUAL' LIMIT 1")
    Account getDefaultAccountSync();

    @Query("SELECT * FROM accounts WHERE isDefault = 1 AND type = 'REAL' LIMIT 1")
    Account getDefaultRealAccountSync();

    @Query("UPDATE accounts SET isDefault = 0 WHERE isDefault = 1 AND type = :type")
    void clearDefaultAccount(String type);

    @Query("UPDATE accounts SET isDefault = 1 WHERE id = :accountId")
    void setDefaultAccount(long accountId);

    /** Replaces the default of this account's type only. */
    @androidx.room.Transaction
    default void makeDefault(Account account) {
        clearDefaultAccount(account.getType());
        setDefaultAccount(account.getId());
    }

    @Insert
    void insert(Account account);

    @Update
    void update(Account account);

    @Delete
    void delete(Account account);
}
