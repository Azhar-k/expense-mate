package com.example.expensemate.data;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "regex_patterns")
public class RegexPattern {
    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "name")
    public String name;

    @ColumnInfo(name = "regex")
    public String regex;

    @ColumnInfo(name = "type") // "DEBIT" or "CREDIT"
    public String type;

    @ColumnInfo(name = "amount_group_index")
    public int amountGroupIndex;

    @ColumnInfo(name = "merchant_group_index")
    public int merchantGroupIndex;

    @ColumnInfo(name = "is_system")
    public boolean isSystem;

    @ColumnInfo(name = "default_sender")
    public String defaultSender;

    public RegexPattern(String name, String regex, String type, int amountGroupIndex, int merchantGroupIndex,
            boolean isSystem, String defaultSender) {
        this.name = name;
        this.regex = regex;
        this.type = type;
        this.amountGroupIndex = amountGroupIndex;
        this.merchantGroupIndex = merchantGroupIndex;
        this.isSystem = isSystem;
        this.defaultSender = defaultSender;
    }
}
