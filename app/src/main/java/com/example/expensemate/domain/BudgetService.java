package com.example.expensemate.domain;

import androidx.lifecycle.LiveData;

import com.example.expensemate.data.Budget;
import com.example.expensemate.data.BudgetProgress;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Monthly limits on everyday spending, one or more categories each. */
public interface BudgetService {
    /**
     * Each budget with what the default fund spent in its categories during the given month.
     *
     * @param month any moment in the month
     */
    LiveData<List<BudgetProgress>> progress(long month);

    /** A new budget goes to the end of a list the user has ordered. */
    CompletableFuture<Long> add(Budget budget, List<String> categories);

    CompletableFuture<Void> update(Budget budget, List<String> categories);

    CompletableFuture<Void> delete(Budget budget);

    /** Numbers the budgets from one in the order given. */
    CompletableFuture<Void> reorder(List<Long> ids);
}
