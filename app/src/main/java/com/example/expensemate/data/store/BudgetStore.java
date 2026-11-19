package com.example.expensemate.data.store;

import androidx.lifecycle.LiveData;

import com.example.expensemate.data.Budget;
import com.example.expensemate.data.BudgetProgress;

import java.util.Date;
import java.util.List;

public interface BudgetStore {
    /** Spending from the default fund between the two dates, inclusive. */
    LiveData<List<BudgetProgress>> observeProgress(Date fromDate, Date toDate);

    /** @return the new row's id */
    long insert(Budget budget);

    void update(Budget budget);

    void deleteById(long id);

    /** Replaces whatever categories the budget covered. */
    void setCategories(long budgetId, List<String> categories);

    int lastSortOrder();

    void setSortOrder(long id, int sortOrder);
}
