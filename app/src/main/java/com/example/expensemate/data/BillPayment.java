package com.example.expensemate.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.expensemate.util.Currencies;

import java.util.Date;

/**
 * One bill's month: paid (or topped up), or skipped because it was not needed. A month with no row
 * is still to pay, or missed once its due date has passed.
 */
@Entity(tableName = "bill_payments", indices = { @Index(value = { "billId", "period" }, unique = true) })
public class BillPayment {
    static final String CREATE_TABLE = "CREATE TABLE IF NOT EXISTS `bill_payments` ("
            + "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
            + "`billId` INTEGER NOT NULL, "
            + "`period` INTEGER NOT NULL, "
            + "`paidOn` INTEGER, "
            + "`amount` REAL NOT NULL, "
            + "`currency` TEXT NOT NULL, "
            + "`transactionId` INTEGER, "
            + "`skipped` INTEGER NOT NULL)";
    static final String CREATE_INDEX = "CREATE UNIQUE INDEX IF NOT EXISTS `index_bill_payments_billId_period` "
            + "ON `bill_payments` (`billId`, `period`)";

    @PrimaryKey(autoGenerate = true)
    private long id;
    private long billId;
    private int period; // See Periods
    private Date paidOn; // Null when skipped
    private double amount; // What was paid, so changing the bill later leaves past months alone
    @NonNull
    private String currency = Currencies.LEGACY;
    private Long transactionId; // The payment or top-up leg it came from, if any
    private boolean skipped;

    public BillPayment() {
    }

    public static BillPayment paid(RecurringPayment bill, int period, Date paidOn, Long transactionId) {
        BillPayment payment = new BillPayment();
        payment.billId = bill.getId();
        payment.period = period;
        payment.paidOn = paidOn;
        payment.amount = bill.getAmount();
        payment.currency = bill.getCurrency();
        payment.transactionId = transactionId;
        return payment;
    }

    public static BillPayment skipped(RecurringPayment bill, int period) {
        BillPayment payment = paid(bill, period, null, null);
        payment.skipped = true;
        payment.amount = 0;
        return payment;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getBillId() { return billId; }
    public void setBillId(long billId) { this.billId = billId; }

    public int getPeriod() { return period; }
    public void setPeriod(int period) { this.period = period; }

    public Date getPaidOn() { return paidOn; }
    public void setPaidOn(Date paidOn) { this.paidOn = paidOn; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    @NonNull
    public String getCurrency() { return currency; }
    public void setCurrency(@NonNull String currency) { this.currency = currency; }

    public Long getTransactionId() { return transactionId; }
    public void setTransactionId(Long transactionId) { this.transactionId = transactionId; }

    public boolean isSkipped() { return skipped; }
    public void setSkipped(boolean skipped) { this.skipped = skipped; }

    /**
     * Rebuilds the months bills were paid in from what older versions kept: every transaction
     * linked to a bill pays the month it happened in, and a bill still ticked was paid in the month
     * it was ticked. A bill without a start month is owed from {@code current}.
     */
    static void backfill(SupportSQLiteDatabase db, int current) {
        String period = "CAST(strftime('%Y%m', DATE / 1000, 'unixepoch', 'localtime') AS INTEGER)";
        db.execSQL("INSERT OR IGNORE INTO bill_payments (billId, period, paidOn, amount, currency, transactionId, "
                + "skipped) SELECT t.linkedRecurringPaymentId, " + period.replace("DATE", "t.date") + ", t.date, "
                + "t.amount, t.currency, t.id, 0 FROM transactions t "
                + "JOIN recurring_payments b ON b.id = t.linkedRecurringPaymentId "
                + "WHERE t.date IS NOT NULL ORDER BY t.date");
        db.execSQL("INSERT OR IGNORE INTO bill_payments (billId, period, paidOn, amount, currency, transactionId, "
                + "skipped) SELECT id, " + period.replace("DATE", "lastCompletedDate") + ", lastCompletedDate, "
                + "amount, currency, NULL, 0 FROM recurring_payments "
                + "WHERE isCompleted = 1 AND lastCompletedDate IS NOT NULL");
        // Months ticked by hand were never kept, so an earlier month with no row may well have been paid.
        // Bills are owed from now on; the earlier months that do have a row still show as paid.
        db.execSQL("UPDATE recurring_payments SET startPeriod = " + current + " WHERE startPeriod = 0");
    }
}
