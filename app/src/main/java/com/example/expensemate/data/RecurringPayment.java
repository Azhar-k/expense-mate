package com.example.expensemate.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import java.util.Date;

@Entity(tableName = "recurring_payments")
public class RecurringPayment {
    @PrimaryKey(autoGenerate = true)
    private long id;
    
    private String name;
    private double amount;
    private int dueDay; // Day of the month (1-31)
    private Date expiryDate;
    private boolean isCompleted;
    private Date lastCompletedDate;

    public RecurringPayment(String name, double amount, int dueDay, Date expiryDate) {
        this.name = name;
        this.amount = amount;
        this.dueDay = dueDay;
        this.expiryDate = expiryDate;
        this.isCompleted = false;
    }

    // Getters and Setters
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }
    
    public int getDueDay() { return dueDay; }
    public void setDueDay(int dueDay) { this.dueDay = dueDay; }
    
    public Date getExpiryDate() { return expiryDate; }
    public void setExpiryDate(Date expiryDate) { this.expiryDate = expiryDate; }
    
    public boolean isCompleted() { return isCompleted; }
    public void setCompleted(boolean completed) { isCompleted = completed; }
    
    public Date getLastCompletedDate() { return lastCompletedDate; }
    public void setLastCompletedDate(Date lastCompletedDate) { this.lastCompletedDate = lastCompletedDate; }
} 