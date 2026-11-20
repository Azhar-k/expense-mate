package com.example.expensemate.data;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.sqlite.db.SupportSQLiteDatabase;
import java.util.Date;

/**
 * Where money is: a bank account, a card, a wallet or cash. Each holds one currency, and every
 * transaction's amount is in its account's currency. What the money is for is its {@link Fund}.
 */
@Entity(tableName = "accounts")
public class Account {
    static final String DEFAULT_NAME = "Bank account";

    @PrimaryKey(autoGenerate = true)
    private long id;

    private String name;
    private String accountNumber;
    private String bank;
    private Date expiryDate;
    private String description;
    private boolean isDefault; // Exactly one: where a transaction goes when no account is chosen
    @ColumnInfo(defaultValue = "0") // Matches the migration, so inserts that leave it out still work
    private int sortOrder; // Lists go by this, then by name; zero until the user reorders
    private String currency; // ISO 4217 code of the account's money

    /** Gives accounts a default, and that default to any transaction without an account. */
    public static void ensureDefault(SupportSQLiteDatabase database) {
        database.execSQL("INSERT INTO accounts (name, accountNumber, bank, description, isDefault, sortOrder) "
                + "SELECT ?, '', '', '', 0, 0 WHERE NOT EXISTS (SELECT 1 FROM accounts)",
                new Object[] { DEFAULT_NAME });
        database.execSQL("UPDATE accounts SET isDefault = 1 WHERE id = (SELECT MIN(id) FROM accounts) "
                + "AND NOT EXISTS (SELECT 1 FROM accounts WHERE isDefault = 1)");
        database.execSQL("UPDATE transactions SET accountId = (SELECT id FROM accounts WHERE isDefault = 1) "
                + "WHERE accountId IS NULL");
    }

    /** An account always says what its money is in; one without gets this currency. */
    public static void ensureCurrencies(SupportSQLiteDatabase database, String currency) {
        database.execSQL("UPDATE accounts SET currency = ? WHERE currency IS NULL", new Object[] { currency });
    }

    public Account(String name, String accountNumber, String bank, Date expiryDate, String description) {
        this.name = name;
        this.accountNumber = accountNumber;
        this.bank = bank;
        this.expiryDate = expiryDate;
        this.description = description;
        this.isDefault = false;
    }

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

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}
