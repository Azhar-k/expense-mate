package com.example.expensemate.data;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.example.expensemate.util.Currencies;

/**
 * A monthly limit on everyday spending in some categories. Which categories it covers are in
 * {@link BudgetCategory}; what has been spent is always worked out, never stored.
 */
@Entity(tableName = "budgets")
public class Budget {
    static final String CREATE_TABLE = "CREATE TABLE IF NOT EXISTS `budgets` ("
            + "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
            + "`name` TEXT NOT NULL, "
            + "`amount` REAL NOT NULL, "
            + "`currency` TEXT NOT NULL, "
            + "`sortOrder` INTEGER NOT NULL DEFAULT 0)";

    @PrimaryKey(autoGenerate = true)
    private long id;
    @NonNull
    private String name = "";
    private double amount; // The limit for one calendar month
    @NonNull
    private String currency = Currencies.LEGACY; // Only spending in this currency counts towards it
    @ColumnInfo(defaultValue = "0") // Matches the migration
    private int sortOrder;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    @NonNull
    public String getName() { return name; }
    public void setName(@NonNull String name) { this.name = name; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    @NonNull
    public String getCurrency() { return currency; }
    public void setCurrency(@NonNull String currency) { this.currency = currency; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}
