package com.example.expensemate.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import java.util.Date;

@Entity(tableName = "accounts")
public class Account {
    @PrimaryKey(autoGenerate = true)
    private long id;
    
    private String name;
    private String accountNumber;
    private String bank;
    private Date expiryDate;
    private String description;
    private boolean isDefault;

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
} 