package com.example.expensemate.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.sqlite.db.SupportSQLiteDatabase;
import java.util.Date;

@Entity(tableName = "accounts")
public class Account {
    public static final String TYPE_VIRTUAL = "VIRTUAL";
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
    private String type = TYPE_VIRTUAL;

    /**
     * Gives real accounts a default, and that default to any transaction without a real account.
     * A transaction whose virtual account is missing is left as it is until someone assigns one.
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
                new Object[] { TYPE_REAL, TYPE_VIRTUAL });
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
} 