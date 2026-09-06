package com.example.expensemate.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;
import androidx.room.Ignore;
import java.util.Date;
import android.util.Log;

@Entity(tableName = "transactions")
public class Transaction {
    @PrimaryKey(autoGenerate = true)
    private long id;
    private double amount;
    private String description;
    
    @TypeConverters(Converters.class)
    private Date date;
    private String transactionType; // "DEBIT" or "CREDIT"
    private String receiverName;
    private String smsBody;
    private String smsSender;
    private String category; // New field for transaction category
    private Long linkedRecurringPaymentId; // ID of the linked recurring payment, null if not linked
    private String smsHash; // Hash of SMS body and sender for duplicate detection
    private Long accountId; // ID of the linked account
    private boolean isExcludedFromSummary; // Whether this transaction should be excluded from summary calculations

    public Transaction() {
        this.category = "Default"; // Default category
        this.date = new Date(); // Initialize with current date
        this.linkedRecurringPaymentId = null;
        this.smsHash = null;
        this.accountId = null;
        this.isExcludedFromSummary = false; // Default to included
    }

    @Ignore
    public Transaction(double amount, String description, Date date,
                      String transactionType, String receiverName,
                      String smsBody, String smsSender) {
        this.amount = amount;
        this.description = description;
        this.date = date != null ? date : new Date();
        this.transactionType = transactionType;
        this.receiverName = receiverName;
        this.smsBody = smsBody;
        this.smsSender = smsSender;
        this.category = "Default"; // Default category
        this.linkedRecurringPaymentId = null;
        this.smsHash = generateSmsHash(smsBody, smsSender, this.date);
        this.isExcludedFromSummary = false; // Default to included
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
        this.date = date != null ? date : new Date();
        if (this.smsBody != null && this.smsSender != null) {
            this.smsHash = generateSmsHash(this.smsBody, this.smsSender, this.date);
        }
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
        if (this.smsBody != null && this.smsSender != null) {
            this.smsHash = generateSmsHash(this.smsBody, this.smsSender, this.date);
        }
    }

    public String getSmsSender() {
        return smsSender;
    }

    public void setSmsSender(String smsSender) {
        this.smsSender = smsSender;
        if (this.smsBody != null && this.smsSender != null) {
            this.smsHash = generateSmsHash(this.smsBody, this.smsSender, this.date);
        }
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category != null ? category : "Default";
    }

    public Long getLinkedRecurringPaymentId() {
        return linkedRecurringPaymentId;
    }

    public void setLinkedRecurringPaymentId(Long linkedRecurringPaymentId) {
        this.linkedRecurringPaymentId = linkedRecurringPaymentId;
    }

    public String getSmsHash() {
        return smsHash;
    }

    public void setSmsHash(String smsHash) {
        this.smsHash = smsHash;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public boolean isExcludedFromSummary() {
        return isExcludedFromSummary;
    }

    public void setExcludedFromSummary(boolean excludedFromSummary) {
        isExcludedFromSummary = excludedFromSummary;
    }

    private String generateSmsHash(String smsBody, String smsSender, Date date) {
        if (smsBody == null || smsSender == null) {
            Log.d("Transaction", "SMS hash generation failed: null body or sender");
            return null;
        }
        // Normalize the strings by trimming and converting to lowercase
        String normalizedBody = smsBody.trim().toLowerCase();
        String normalizedSender = smsSender.trim().toLowerCase();
        String dateStr = "";
        if (date != null) {
            dateStr = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(date);
        }
        String combined = normalizedBody + "|" + normalizedSender + "|" + dateStr;
        String hash = String.valueOf(combined.hashCode());
        return hash;
    }
}