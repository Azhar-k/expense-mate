package com.example.expensemate.domain.local;

import androidx.lifecycle.LiveData;

import com.example.expensemate.data.Budget;
import com.example.expensemate.data.BudgetProgress;
import com.example.expensemate.data.store.BudgetStore;
import com.example.expensemate.domain.BudgetService;

import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;

final class LocalBudgetService implements BudgetService {
    private final BudgetStore store;
    private final Writer writer;

    LocalBudgetService(BudgetStore store, Writer writer) {
        this.store = store;
        this.writer = writer;
    }

    @Override
    public LiveData<List<BudgetProgress>> progress(long month) {
        Calendar from = Calendar.getInstance();
        from.setTimeInMillis(month);
        from.set(Calendar.DAY_OF_MONTH, 1);
        from.set(Calendar.HOUR_OF_DAY, 0);
        from.set(Calendar.MINUTE, 0);
        from.set(Calendar.SECOND, 0);
        from.set(Calendar.MILLISECOND, 0);
        Calendar to = (Calendar) from.clone();
        to.add(Calendar.MONTH, 1);
        to.add(Calendar.MILLISECOND, -1);
        return store.observeProgress(from.getTime(), new Date(to.getTimeInMillis()));
    }

    @Override
    public CompletableFuture<Long> add(Budget budget, List<String> categories) {
        return writer.call(() -> {
            int last = store.lastSortOrder();
            budget.setSortOrder(last == 0 ? 0 : last + 1);
            long id = store.insert(budget);
            budget.setId(id);
            store.setCategories(id, categories);
            return id;
        });
    }

    @Override
    public CompletableFuture<Void> update(Budget budget, List<String> categories) {
        return writer.run(() -> {
            store.update(budget);
            store.setCategories(budget.getId(), categories);
        });
    }

    @Override
    public CompletableFuture<Void> delete(Budget budget) {
        return writer.run(() -> {
            store.setCategories(budget.getId(), Collections.emptyList());
            store.deleteById(budget.getId());
        });
    }

    @Override
    public CompletableFuture<Void> reorder(List<Long> ids) {
        return writer.run(() -> {
            for (int i = 0; i < ids.size(); i++) {
                store.setSortOrder(ids.get(i), i + 1);
            }
        });
    }
}
