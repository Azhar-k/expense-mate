package com.example.expensemate.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;

/** One category a budget covers. Transactions name their category, so this does too. */
@Entity(tableName = "budget_categories", primaryKeys = { "budgetId", "category" })
public class BudgetCategory {
    static final String CREATE_TABLE = "CREATE TABLE IF NOT EXISTS `budget_categories` ("
            + "`budgetId` INTEGER NOT NULL, "
            + "`category` TEXT NOT NULL, "
            + "PRIMARY KEY(`budgetId`, `category`))";

    public long budgetId;
    @NonNull
    public String category;

    public BudgetCategory(long budgetId, @NonNull String category) {
        this.budgetId = budgetId;
        this.category = category;
    }
}
