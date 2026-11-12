package com.example.expensemate.data;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;

import com.example.expensemate.util.Currencies;
import androidx.room.PrimaryKey;

import java.util.Date;

/**
 * Money lent to someone. Returns come from credits linked through {@link Transaction#getLoanId()}
 * and from returns recorded by hand, so how much is back is always worked out, never stored.
 */
@Entity(tableName = "loans")
public class Loan {
    @PrimaryKey(autoGenerate = true)
    private long id;
    @NonNull
    private String person = "";
    private double amount;
    private Date date;
    private String note;
    private double returnedManually; // Returns that never showed up as a transaction, such as cash
    private boolean closedManually; // Marked as returned by hand, whatever came back
    private Date closedDate; // Last done by hand: marked as returned, or a return recorded
    @NonNull
    @ColumnInfo(defaultValue = Currencies.LEGACY) // Matches the migration
    private String currency = Currencies.LEGACY; // What it was lent in; only returns in it count

    @NonNull
    public String getCurrency() { return currency; }
    public void setCurrency(@NonNull String currency) { this.currency = currency; }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    @NonNull
    public String getPerson() { return person; }
    public void setPerson(@NonNull String person) { this.person = person; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public Date getDate() { return date; }
    public void setDate(Date date) { this.date = date; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public double getReturnedManually() { return returnedManually; }
    public void setReturnedManually(double returnedManually) { this.returnedManually = returnedManually; }

    public boolean isClosedManually() { return closedManually; }
    public void setClosedManually(boolean closedManually) { this.closedManually = closedManually; }

    public Date getClosedDate() { return closedDate; }
    public void setClosedDate(Date closedDate) { this.closedDate = closedDate; }
}
