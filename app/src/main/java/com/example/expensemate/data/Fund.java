package com.example.expensemate.data;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.sqlite.db.SupportSQLiteDatabase;

import java.util.Date;

/**
 * What money is for: everyday spending, a trip, health, savings. A fund holds no money of its own;
 * its balance is what its transactions add up to, in each currency they are in. The default fund is
 * everyday money, the one budgets keep track of. Where the money is, is its {@link Account}.
 */
@Entity(tableName = "funds")
public class Fund {
    static final String DEFAULT_NAME = "Everyday";

    @PrimaryKey(autoGenerate = true)
    private long id;
    @NonNull
    private String name = "";
    private String description;
    private boolean isDefault; // Exactly one: everyday money, where a transaction goes when no fund is chosen
    @ColumnInfo(defaultValue = "0") // Matches the migration
    private int sortOrder; // Lists go by this, then by name; zero until the user reorders
    private Double target; // What it is saving up to, in the main currency; null when it has no goal
    private Date targetDate; // When it should reach the target; optional

    /** Gives funds a default, and that default to any transaction without a fund. */
    public static void ensureDefault(SupportSQLiteDatabase database) {
        database.execSQL("INSERT INTO funds (name, description, isDefault, sortOrder) "
                + "SELECT ?, '', 0, 0 WHERE NOT EXISTS (SELECT 1 FROM funds)", new Object[] { DEFAULT_NAME });
        database.execSQL("UPDATE funds SET isDefault = 1 WHERE id = (SELECT MIN(id) FROM funds) "
                + "AND NOT EXISTS (SELECT 1 FROM funds WHERE isDefault = 1)");
        database.execSQL("UPDATE transactions SET fundId = (SELECT id FROM funds WHERE isDefault = 1) "
                + "WHERE fundId IS NULL");
    }

    public Fund() {
    }

    public Fund(@NonNull String name, String description) {
        this.name = name;
        this.description = description;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    @NonNull
    public String getName() { return name; }
    public void setName(@NonNull String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean isDefault) { this.isDefault = isDefault; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    public Double getTarget() { return target; }
    public void setTarget(Double target) { this.target = target; }

    public Date getTargetDate() { return targetDate; }
    public void setTargetDate(Date targetDate) { this.targetDate = targetDate; }
}
