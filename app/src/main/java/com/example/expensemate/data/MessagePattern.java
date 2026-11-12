package com.example.expensemate.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.sqlite.db.SupportSQLiteDatabase;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * One way of reading a bank message. The regular expression names the parts it captures:
 * {@code (?<amount>...)} is required, {@code (?<merchant>...)} gives the counterparty.
 */
@Entity(tableName = "message_patterns", indices = { @Index(value = "builtinKey", unique = true) })
public class MessagePattern {
    public static final String CREATE_TABLE = "CREATE TABLE IF NOT EXISTS `message_patterns` ("
            + "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
            + "`name` TEXT NOT NULL, "
            + "`pattern` TEXT NOT NULL, "
            + "`transactionType` TEXT NOT NULL, "
            + "`counterparty` TEXT, "
            + "`excludeFromSummary` INTEGER NOT NULL, "
            + "`active` INTEGER NOT NULL, "
            + "`sortOrder` INTEGER NOT NULL, "
            + "`builtinKey` TEXT)";
    public static final String CREATE_INDEX = "CREATE UNIQUE INDEX IF NOT EXISTS `index_message_patterns_builtinKey` "
            + "ON `message_patterns` (`builtinKey`)";

    @PrimaryKey(autoGenerate = true)
    private long id;

    @NonNull
    private String name = "";

    @NonNull
    private String pattern = "";

    @NonNull
    private String transactionType = "DEBIT"; // "DEBIT" or "CREDIT"

    // Fixed text for the counterparty, where {merchant} stands for the captured merchant.
    // Null or empty uses the merchant as it is.
    @Nullable
    private String counterparty;

    private boolean excludeFromSummary; // For messages whose money another message already records

    private boolean active = true;

    private int sortOrder; // Lower runs first; the first pattern that matches wins

    // Set on the patterns the app ships with, so later versions can find and update them.
    // A later migration should only update rows whose pattern text is still the shipped one,
    // so a user's edits are kept.
    @Nullable
    private String builtinKey;

    private static final Map<String, Pattern> COMPILED = new ConcurrentHashMap<>();

    public MessagePattern() {
    }

    @Ignore
    public MessagePattern(@NonNull String name, @NonNull String transactionType, @NonNull String pattern) {
        this.name = name;
        this.transactionType = transactionType;
        this.pattern = pattern;
    }

    /** Inserts the shipped patterns that are missing, keeping their order and any the user changed. */
    public static void ensureBuiltIns(SupportSQLiteDatabase database) {
        for (MessagePattern p : DefaultMessagePatterns.all()) {
            database.execSQL("INSERT OR IGNORE INTO message_patterns (name, pattern, transactionType, counterparty, "
                            + "excludeFromSummary, active, sortOrder, builtinKey) VALUES (?, ?, ?, ?, ?, 1, ?, ?)",
                    new Object[] { p.name, p.pattern, p.transactionType, p.counterparty,
                            p.excludeFromSummary ? 1 : 0, p.sortOrder, p.builtinKey });
        }
    }

    /**
     * The compiled expression, or null if the text is not a valid one. Rows are read fresh for
     * each message, so compiled expressions are kept by their text across reads.
     */
    @Nullable
    public Pattern compiled() {
        Pattern cached = COMPILED.get(pattern);
        if (cached != null) {
            return cached;
        }
        try {
            cached = Pattern.compile(pattern);
        } catch (RuntimeException e) {
            return null;
        }
        if (COMPILED.size() > 200) {
            COMPILED.clear(); // Only edits add entries, so this is rare
        }
        COMPILED.put(pattern, cached);
        return cached;
    }

    public boolean isBuiltin() {
        return builtinKey != null;
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
    public String getPattern() {
        return pattern;
    }

    public void setPattern(@NonNull String pattern) {
        this.pattern = pattern;
    }

    @NonNull
    public String getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(@NonNull String transactionType) {
        this.transactionType = transactionType;
    }

    @Nullable
    public String getCounterparty() {
        return counterparty;
    }

    public void setCounterparty(@Nullable String counterparty) {
        this.counterparty = counterparty;
    }

    public boolean isExcludeFromSummary() {
        return excludeFromSummary;
    }

    public void setExcludeFromSummary(boolean excludeFromSummary) {
        this.excludeFromSummary = excludeFromSummary;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    @Nullable
    public String getBuiltinKey() {
        return builtinKey;
    }

    public void setBuiltinKey(@Nullable String builtinKey) {
        this.builtinKey = builtinKey;
    }
}
