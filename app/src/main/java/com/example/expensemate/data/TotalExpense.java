package com.example.expensemate.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "total_expense")
public class TotalExpense {
    @PrimaryKey
    private int id = 1; // We'll only have one row
    private double amount;

    public TotalExpense(double amount) {
        this.amount = amount;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }
} 