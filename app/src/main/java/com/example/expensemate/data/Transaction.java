package com.example.expensemate.data;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;

import com.example.expensemate.util.Currencies;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;
import androidx.room.Ignore;
import java.util.Date;
import android.util.Log;

@Entity(tableName = "transactions", indices = {
        @Index(value = {"dedupKey", "messageTime"}),
        @Index(value = {"matchKey", "messageTime"}),
        @Index(value = {"transferGroupId"}),
        @Index(value = {"loanId"}),
        @Index(value = {"date"}),
        @Index(value = {"smsHash"})
})
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
    private Long accountId; // ID of the linked virtual account
    private Long realAccountId; // ID of the linked real account
    private boolean isExcludedFromSummary; // Whether this transaction should be excluded from summary calculations
    private String dedupKey; // Hash of the normalized message text, null for manual entries
    private String matchKey; // Amount, type and merchant, for alerts that have no full text
    private Long messageTime; // When the message arrived; date can be edited, this cannot
    private String source; // Entry path that created the row, see TransactionKeys
    private String transferGroupId; // Shared by the two sides of a transfer, null otherwise
    private boolean needsReview; // Captured from a message and still waiting for a category
    private Long loanId; // Money lent that this row gave out (a debit) or brought back (a credit)
    @NonNull
    @ColumnInfo(defaultValue = Currencies.LEGACY) // Matches the migration
    private String currency = Currencies.LEGACY; // ISO 4217 code of amount: its real account's currency

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

    public Long getRealAccountId() {
        return realAccountId;
    }

    public void setRealAccountId(Long realAccountId) {
        this.realAccountId = realAccountId;
    }

    public boolean isExcludedFromSummary() {
        return isExcludedFromSummary;
    }

    public void setExcludedFromSummary(boolean excludedFromSummary) {
        isExcludedFromSummary = excludedFromSummary;
    }

    public String getDedupKey() {
        return dedupKey;
    }

    public void setDedupKey(String dedupKey) {
        this.dedupKey = dedupKey;
    }

    public String getMatchKey() {
        return matchKey;
    }

    public void setMatchKey(String matchKey) {
        this.matchKey = matchKey;
    }

    public Long getMessageTime() {
        return messageTime;
    }

    public void setMessageTime(Long messageTime) {
        this.messageTime = messageTime;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getTransferGroupId() {
        return transferGroupId;
    }

    public void setTransferGroupId(String transferGroupId) {
        this.transferGroupId = transferGroupId;
    }

    public boolean isNeedsReview() {
        return needsReview;
    }

    public void setNeedsReview(boolean needsReview) {
        this.needsReview = needsReview;
    }

    @NonNull
    public String getCurrency() {
        return currency;
    }

    public void setCurrency(@NonNull String currency) {
        this.currency = currency;
    }

    public Long getLoanId() {
        return loanId;
    }

    public void setLoanId(Long loanId) {
        this.loanId = loanId;
    }

    public boolean isTransfer() {
        return transferGroupId != null;
    }

    /** Rows are shared with list adapters, so edits work on a copy and let the database refresh the list. */
    public Transaction copy() {
        Transaction copy = new Transaction();
        copy.id = id;
        copy.amount = amount;
        copy.description = description;
        copy.date = date;
        copy.transactionType = transactionType;
        copy.receiverName = receiverName;
        copy.smsBody = smsBody;
        copy.smsSender = smsSender;
        copy.category = category;
        copy.linkedRecurringPaymentId = linkedRecurringPaymentId;
        copy.smsHash = smsHash;
        copy.accountId = accountId;
        copy.realAccountId = realAccountId;
        copy.isExcludedFromSummary = isExcludedFromSummary;
        copy.dedupKey = dedupKey;
        copy.matchKey = matchKey;
        copy.messageTime = messageTime;
        copy.source = source;
        copy.transferGroupId = transferGroupId;
        copy.needsReview = needsReview;
        copy.loanId = loanId;
        copy.currency = currency;
        return copy;
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