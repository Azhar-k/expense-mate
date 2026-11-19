package com.example.expensemate.data;

import androidx.room.Embedded;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** A budget with what has been spent against it in one month. */
public class BudgetProgress {
    static final String SEPARATOR = "\n";

    @Embedded
    public Budget budget;
    public double spent;
    /** The covered categories joined by {@link #SEPARATOR}, or null when it covers none. */
    public String categoryList;

    public List<String> categories() {
        if (categoryList == null || categoryList.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> names = new ArrayList<>(Arrays.asList(categoryList.split(SEPARATOR)));
        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    /** Below zero once the month has gone over. */
    public double left() {
        return budget.getAmount() - spent;
    }

    public boolean isOver() {
        return spent > budget.getAmount();
    }

    /** How full the month is, from 0 to 100. */
    public int percent() {
        if (budget.getAmount() <= 0) {
            return spent > 0 ? 100 : 0;
        }
        return (int) Math.min(100, Math.round(spent * 100 / budget.getAmount()));
    }
}
