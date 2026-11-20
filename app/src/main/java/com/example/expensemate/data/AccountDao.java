package com.example.expensemate.data;

import androidx.lifecycle.LiveData;
import androidx.room.*;
import java.util.List;

/** Where money is. Funds, what it is for, are in {@link FundDao}. */
@Dao
public interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY sortOrder ASC, name ASC")
    LiveData<List<Account>> getAllAccounts();

    @Query("SELECT * FROM accounts ORDER BY sortOrder ASC, name ASC")
    List<Account> getAllAccountsSync();

    @Query("SELECT * FROM accounts WHERE isDefault = 1 LIMIT 1")
    LiveData<Account> getDefaultAccount();

    @Query("SELECT * FROM accounts WHERE isDefault = 1 LIMIT 1")
    Account getDefaultAccountSync();

    @Query("SELECT * FROM accounts WHERE id = :id")
    Account getAccountByIdSync(long id);

    @Query("UPDATE accounts SET isDefault = 0 WHERE isDefault = 1")
    void clearDefaultAccount();

    @Query("UPDATE accounts SET isDefault = 1 WHERE id = :accountId")
    void setDefaultAccount(long accountId);

    @Insert
    long insert(Account account);

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM accounts")
    int lastSortOrder();

    @Query("UPDATE accounts SET sortOrder = :sortOrder WHERE id = :id")
    void setSortOrder(long id, int sortOrder);

    @Update
    void update(Account account);

    @Delete
    void delete(Account account);
}
