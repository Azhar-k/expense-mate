package com.example.expensemate.data.room;

import androidx.lifecycle.LiveData;

import com.example.expensemate.data.Budget;
import com.example.expensemate.data.BudgetCategory;
import com.example.expensemate.data.BudgetDao;
import com.example.expensemate.data.BudgetProgress;
import com.example.expensemate.data.store.BudgetStore;

import java.util.Date;
import java.util.List;

final class RoomBudgetStore implements BudgetStore {
    private final BudgetDao dao;

    RoomBudgetStore(BudgetDao dao) {
        this.dao = dao;
    }

    @Override
    public LiveData<List<BudgetProgress>> observeProgress(Date fromDate, Date toDate) {
        return dao.getProgress(fromDate, toDate);
    }

    @Override
    public long insert(Budget budget) {
        return dao.insert(budget);
    }

    @Override
    public void update(Budget budget) {
        dao.update(budget);
    }

    @Override
    public void deleteById(long id) {
        dao.deleteById(id);
    }

    @Override
    public void setCategories(long budgetId, List<String> categories) {
        dao.clearCategories(budgetId);
        for (String category : categories) {
            dao.insertCategory(new BudgetCategory(budgetId, category));
        }
    }

    @Override
    public int lastSortOrder() {
        return dao.lastSortOrder();
    }

    @Override
    public void setSortOrder(long id, int sortOrder) {
        dao.setSortOrder(id, sortOrder);
    }
}
