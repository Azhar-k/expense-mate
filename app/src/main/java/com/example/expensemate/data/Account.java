package com.example.expensemate.data;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.sqlite.db.SupportSQLiteDatabase;
import java.util.Date;

@Entity(tableName = "accounts")
public class Account {
    // Funds were called virtual accounts; the stored value stays so older backups still restore
    public static final String TYPE_FUND = "VIRTUAL";
    public static final String TYPE_REAL = "REAL";
    static final String DEFAULT_REAL_NAME = "Bank account";

    @PrimaryKey(autoGenerate = true)
    private long id;
    
    private String name;
    private String accountNumber;
    private String bank;
    private Date expiryDate;
    private String description;
    private boolean isDefault; // One per type
    @NonNull
    private String type = TYPE_FUND;
    @ColumnInfo(defaultValue = "0") // Matches the migration, so inserts that leave it out still work
    private int sortOrder; // Lists go by this, then by name; zero until the user reorders
    private String currency; // ISO 4217 code of a real account's money; null on funds, which hold any
    private Double target; // What a fund is saving up to; null when it has no goal
    private Date targetDate; // When a fund with a target should reach it; optional

    /**
     * Gives real accounts a default, and that default to any transaction without a real account.
     * A transaction whose fund is missing is left as it is until someone assigns one.
     */
    public static void ensureDefaultRealAccount(SupportSQLiteDatabase database) {
        database.execSQL("INSERT INTO accounts (name, accountNumber, bank, description, isDefault, type) "
                + "SELECT ?, '', '', '', 0, ? WHERE NOT EXISTS (SELECT 1 FROM accounts WHERE type = ?)",
                new Object[] { DEFAULT_REAL_NAME, TYPE_REAL, TYPE_REAL });
        database.execSQL("UPDATE accounts SET isDefault = 1 WHERE id = (SELECT MIN(id) FROM accounts WHERE type = ?) "
                + "AND NOT EXISTS (SELECT 1 FROM accounts WHERE type = ? AND isDefault = 1)",
                new Object[] { TYPE_REAL, TYPE_REAL });
        database.execSQL("UPDATE transactions SET realAccountId = "
                + "(SELECT id FROM accounts WHERE type = ? AND isDefault = 1) WHERE realAccountId IS NULL "
                + "AND accountId IN (SELECT id FROM accounts WHERE type = ?)",
                new Object[] { TYPE_REAL, TYPE_FUND });
    }

    /** A real account always says what its money is in; one without gets this currency. */
    public static void ensureRealAccountCurrencies(SupportSQLiteDatabase database, String currency) {
        database.execSQL("UPDATE accounts SET currency = ? WHERE type = ? AND currency IS NULL",
                new Object[] { currency, TYPE_REAL });
    }

    public Account(String name, String accountNumber, String bank, Date expiryDate, String description) {
        this.name = name;
        this.accountNumber = accountNumber;
        this.bank = bank;
        this.expiryDate = expiryDate;
        this.description = description;
        this.isDefault = false;
    }

    // Getters and Setters
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
    
    public String getBank() { return bank; }
    public void setBank(String bank) { this.bank = bank; }
    
    public Date getExpiryDate() { return expiryDate; }
    public void setExpiryDate(Date expiryDate) { this.expiryDate = expiryDate; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean isDefault) { this.isDefault = isDefault; }

    @NonNull
    public String getType() { return type; }
    public void setType(@NonNull String type) { this.type = type; }

    public boolean isReal() { return TYPE_REAL.equals(type); }

    public boolean isFund() { return !isReal(); }

    public Double getTarget() { return target; }
    public void setTarget(Double target) { this.target = target; }

    public Date getTargetDate() { return targetDate; }
    public void setTargetDate(Date targetDate) { this.targetDate = targetDate; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
} 