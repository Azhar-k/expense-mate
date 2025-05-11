package com.example.expensemate.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;
import java.util.Date;

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

    public Transaction() {
        this.category = "Others"; // Default category
        this.date = new Date(); // Initialize with current date
        this.linkedRecurringPaymentId = null;
        this.smsHash = null;
    }

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
        this.category = "Others"; // Default category
        this.linkedRecurringPaymentId = null;
        this.smsHash = generateSmsHash(smsBody, smsSender);
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
        this.category = category != null ? category : "Others";
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

    private String generateSmsHash(String smsBody, String smsSender) {
        if (smsBody == null || smsSender == null) {
            return null;
        }
        String combined = smsBody + "|" + smsSender;
        return String.valueOf(combined.hashCode());
    }
}