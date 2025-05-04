package com.example.expensemate.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

@Entity(tableName = "categories")
public class Category {
    @PrimaryKey(autoGenerate = true)
    private long id;
    
    @NonNull
    private String name;
    
    @NonNull
    private String type; // "EXPENSE" or "INCOME"

    public Category(@NonNull String name, @NonNull String type) {
        this.name = name;
        this.type = type;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    @NonNull
    public String getName() {
        return name;
    }

    public void setName(@NonNull String name) {
        this.name = name;
    }

    @NonNull
    public String getType() {
        return type;
    }

    public void setType(@NonNull String type) {
        this.type = type;
    }
} 