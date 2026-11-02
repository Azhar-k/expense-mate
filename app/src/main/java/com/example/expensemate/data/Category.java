package com.example.expensemate.data;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Entity(tableName = "categories")
public class Category {
    @PrimaryKey(autoGenerate = true)
    private long id;
    
    @NonNull
    private String name;
    
    @NonNull
    private String type; // "EXPENSE" or "INCOME"

    private boolean isDefault; // Goes on captured payments; each type has exactly one

    @ColumnInfo(defaultValue = "0") // Matches the migration, so inserts that leave it out still work
    private int sortOrder; // Lists go by this, then by name; zero until the user reorders

    public Category(@NonNull String name, @NonNull String type) {
        this.name = name;
        this.type = type;
    }

    /** Gives each type a default, using its Default category if it has one. Backups from before this field need it too. */
    public static void ensureDefaults(SupportSQLiteDatabase database) {
        database.execSQL("UPDATE categories SET isDefault = 1 WHERE id IN (SELECT MIN(c.id) FROM categories c "
                + "WHERE c.name = 'Default' AND NOT EXISTS "
                + "(SELECT 1 FROM categories d WHERE d.type = c.type AND d.isDefault = 1) GROUP BY c.type)");
        database.execSQL("INSERT INTO categories (name, type, isDefault) SELECT 'Default', t.type, 1 FROM "
                + "(SELECT 'EXPENSE' AS type UNION ALL SELECT 'INCOME') t WHERE NOT EXISTS "
                + "(SELECT 1 FROM categories c WHERE c.type = t.type AND c.isDefault = 1)");
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    @NonNull
    public String getName() {
        return name;
    }

    public void setName(@NonNull String name) {
        this.name = name;
    }

    @NonNull
    public String getType() {
        return type;
    }

    public void setType(@NonNull String type) {
        this.type = type;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }
} 