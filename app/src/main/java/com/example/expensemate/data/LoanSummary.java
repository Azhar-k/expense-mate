package com.example.expensemate.data;

import androidx.room.Embedded;

import java.util.Date;

/** A loan with how much of it has come back. */
public class LoanSummary {
    @Embedded
    public Loan loan;
    public double returned;
    public Date lastReturnDate;

    public boolean isCompleted() {
        return loan.isClosedManually() || returned >= loan.getAmount() - 0.005;
    }

    /** Fully returned on its own, so reopening it by hand would not change anything. */
    public boolean isFullyReturned() {
        return returned >= loan.getAmount() - 0.005;
    }

    public double outstanding() {
        return isCompleted() ? 0 : Math.max(0, loan.getAmount() - returned);
    }

    /** When it was marked returned, or when the last of it came back. */
    public Date completedDate() {
        if (loan.isClosedManually() && loan.getClosedDate() != null) {
            return loan.getClosedDate();
        }
        return lastReturnDate;
    }
}
