package com.example.expensemate.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.Date;
import java.util.List;

@Dao
public interface BudgetDao {
    /**
     * Budgets cover everyday money: debits from the default fund. Money set aside in another fund was
     * planned when it went in, so spending it does not count again. Transfers, rows left out of the
     * summary and other currencies do not count either.
     */
    String PROGRESS = "SELECT b.*, "
            + "(SELECT GROUP_CONCAT(bc.category, '" + BudgetProgress.SEPARATOR + "') FROM budget_categories bc "
            + "WHERE bc.budgetId = b.id) AS categoryList, "
            + "COALESCE((SELECT SUM(t.amount) FROM transactions t "
            + "WHERE t.transactionType = 'DEBIT' AND t.transferGroupId IS NULL AND t.isExcludedFromSummary = 0 "
            + "AND t.currency = b.currency AND t.date BETWEEN :fromDate AND :toDate "
            + "AND t.accountId = (SELECT a.id FROM accounts a WHERE a.type = '" + Account.TYPE_FUND + "' "
            + "AND a.isDefault = 1 LIMIT 1) "
            + "AND t.category IN (SELECT bc.category FROM budget_categories bc WHERE bc.budgetId = b.id)), 0) AS spent "
            + "FROM budgets b ORDER BY b.sortOrder ASC, b.name ASC";

    @Query(PROGRESS)
    LiveData<List<BudgetProgress>> getProgress(Date fromDate, Date toDate);

    @Query(PROGRESS)
    List<BudgetProgress> getProgressSync(Date fromDate, Date toDate);

    @Query("SELECT * FROM budgets ORDER BY sortOrder ASC, name ASC")
    List<Budget> getAllSync();

    @Query("SELECT * FROM budget_categories ORDER BY budgetId, category")
    List<BudgetCategory> getAllCategoriesSync();

    @Insert
    long insert(Budget budget);

    @Update
    void update(Budget budget);

    @Query("DELETE FROM budgets WHERE id = :id")
    void deleteById(long id);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertCategory(BudgetCategory category);

    @Query("DELETE FROM budget_categories WHERE budgetId = :budgetId")
    void clearCategories(long budgetId);

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM budgets")
    int lastSortOrder();

    @Query("UPDATE budgets SET sortOrder = :sortOrder WHERE id = :id")
    void setSortOrder(long id, int sortOrder);
}
