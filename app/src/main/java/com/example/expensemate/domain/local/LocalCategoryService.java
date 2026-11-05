package com.example.expensemate.domain.local;

import androidx.lifecycle.LiveData;

import com.example.expensemate.data.Category;
import com.example.expensemate.data.store.CategoryStore;
import com.example.expensemate.domain.CategoryService;

import java.util.List;
import java.util.concurrent.CompletableFuture;

final class LocalCategoryService implements CategoryService {
    private final CategoryStore store;
    private final Writer writer;

    LocalCategoryService(CategoryStore store, Writer writer) {
        this.store = store;
        this.writer = writer;
    }

    @Override
    public LiveData<List<Category>> categories() {
        return store.observeAll();
    }

    @Override
    public LiveData<List<Category>> byType(String type) {
        return store.observeByType(type);
    }

    @Override
    public CompletableFuture<Void> add(Category category) {
        return writer.run(() -> insertAtEnd(category));
    }

    private long insertAtEnd(Category category) {
        int last = store.lastSortOrder(category.getType());
        category.setSortOrder(last == 0 ? 0 : last + 1);
        return store.insert(category);
    }

    @Override
    public CompletableFuture<Void> update(Category category) {
        return writer.run(() -> store.update(category));
    }

    @Override
    public CompletableFuture<Void> reorder(List<Long> ids) {
        return writer.run(() -> {
            for (int i = 0; i < ids.size(); i++) {
                store.setSortOrder(ids.get(i), i + 1);
            }
        });
    }

    @Override
    public CompletableFuture<Void> saveAsDefault(Category category) {
        return writer.run(() -> {
            if (category.getId() == 0) {
                category.setId(insertAtEnd(category));
            } else {
                store.update(category);
            }
            store.clearDefault(category.getType());
            store.setDefault(category.getId());
        });
    }

    @Override
    public CompletableFuture<Void> delete(Category category) {
        return writer.run(() -> store.delete(category));
    }
}
