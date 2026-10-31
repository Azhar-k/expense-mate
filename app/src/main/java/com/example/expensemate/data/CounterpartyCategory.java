package com.example.expensemate.data;

import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.sqlite.db.SupportSQLiteDatabase;

/**
 * The category last chosen for a sender or receiver, applied to their next captured payment.
 * It is kept per direction because expense and income categories are separate lists, and it
 * points at the category's id so renaming a category keeps the mapping.
 */
@Entity(tableName = "counterparty_categories", primaryKeys = {"counterparty", "transactionType"})
public class CounterpartyCategory {
    @NonNull
    private String counterparty; // See TransactionKeys.counterpartyKey
    @NonNull
    private String transactionType; // "DEBIT" or "CREDIT"
    private long categoryId;

    public CounterpartyCategory(@NonNull String counterparty, @NonNull String transactionType, long categoryId) {
        this.counterparty = counterparty;
        this.transactionType = transactionType;
        this.categoryId = categoryId;
    }

    @NonNull
    public String getCounterparty() {
        return counterparty;
    }

    public void setCounterparty(@NonNull String counterparty) {
        this.counterparty = counterparty;
    }

    @NonNull
    public String getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(@NonNull String transactionType) {
        this.transactionType = transactionType;
    }

    public long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(long categoryId) {
        this.categoryId = categoryId;
    }

    /**
     * Rebuilds the mappings from the categories already on transactions; the newest row wins.
     * Backups do not carry the mappings, so a restore comes through here as well.
     */
    public static void rebuildFromTransactions(SupportSQLiteDatabase database) {
        database.execSQL("DELETE FROM counterparty_categories");
        String sql = "SELECT t.receiverName, t.transactionType, c.id FROM transactions t JOIN categories c "
                + "ON c.name = t.category AND c.type = CASE t.transactionType WHEN 'CREDIT' THEN 'INCOME' ELSE 'EXPENSE' END "
                + "WHERE t.transactionType IS NOT NULL AND t.transferGroupId IS NULL AND t.needsReview = 0 "
                + "AND t.category <> 'Default' ORDER BY t.date ASC, t.id ASC";
        try (Cursor cursor = database.query(sql)) {
            while (cursor.moveToNext()) {
                String counterparty = TransactionKeys.counterpartyKey(cursor.getString(0));
                if (counterparty != null) {
                    database.execSQL("INSERT OR REPLACE INTO counterparty_categories "
                                    + "(counterparty, transactionType, categoryId) VALUES (?, ?, ?)",
                            new Object[] { counterparty, cursor.getString(1), cursor.getLong(2) });
                }
            }
        }
    }
}
