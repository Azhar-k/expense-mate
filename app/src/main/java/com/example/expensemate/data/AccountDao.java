package com.example.expensemate.data;

import androidx.lifecycle.LiveData;
import androidx.room.*;
import java.util.List;

@Dao
public interface AccountDao {
    // Virtual accounts first, so every list reads the same way
    @Query("SELECT * FROM accounts ORDER BY type DESC, sortOrder ASC, name ASC")
    LiveData<List<Account>> getAllAccounts();

    @Query("SELECT * FROM accounts ORDER BY type DESC, sortOrder ASC, name ASC")
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

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM accounts WHERE type = :type")
    int lastSortOrder(String type);

    /** A new account goes to the end of a list the user has ordered, or into the alphabetical one. */
    @androidx.room.Transaction
    default void insertAtEnd(Account account) {
        int last = lastSortOrder(account.getType());
        account.setSortOrder(last == 0 ? 0 : last + 1);
        insert(account);
    }

    @Query("UPDATE accounts SET sortOrder = :sortOrder WHERE id = :id")
    void setSortOrder(long id, int sortOrder);

    /** Numbers the accounts from one in the order given. */
    @androidx.room.Transaction
    default void saveOrder(List<Long> ids) {
        for (int i = 0; i < ids.size(); i++) {
            setSortOrder(ids.get(i), i + 1);
        }
    }

    @Update
    void update(Account account);

    @Delete
    void delete(Account account);
}
