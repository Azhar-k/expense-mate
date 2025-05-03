package com.example.expensemate.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import java.util.Date;

@Entity(tableName = "transactions")
public class Transaction {
    @PrimaryKey(autoGenerate = true)
    private long id;
    private double amount;
    private String description;
    private Date date;
    private String accountNumber;
    private String accountType; // "BANK" or "CREDIT_CARD"
    private String transactionType; // "DEBIT" or "CREDIT"
    private String receiverName;
    private String smsBody;
    private String smsSender;
    private String category; // New field for transaction category

    public Transaction() {
        this.category = "Others"; // Default category
    }

    public Transaction(double amount, String description, Date date, String accountNumber,
                      String accountType, String transactionType, String receiverName,
                      String smsBody, String smsSender) {
        this.amount = amount;
        this.description = description;
        this.date = date;
        this.accountNumber = accountNumber;
        this.accountType = accountType;
        this.transactionType = transactionType;
        this.receiverName = receiverName;
        this.smsBody = smsBody;
        this.smsSender = smsSender;
        this.category = "Others"; // Default category
    }

    // Getters and Setters
    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public String getAccountType() {
        return accountType;
    }

    public void setAccountType(String accountType) {
        this.accountType = accountType;
    }

    public String getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(String transactionType) {
        this.transactionType = transactionType;
    }

    public String getReceiverName() {
        return receiverName;
    }

    public void setReceiverName(String receiverName) {
        this.receiverName = receiverName;
    }

    public String getSmsBody() {
        return smsBody;
    }

    public void setSmsBody(String smsBody) {
        this.smsBody = smsBody;
    }

    public String getSmsSender() {
        return smsSender;
    }

    public void setSmsSender(String smsSender) {
        this.smsSender = smsSender;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }
} 