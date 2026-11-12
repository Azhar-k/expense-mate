package com.example.expensemate.domain.local;

import androidx.lifecycle.LiveData;

import com.example.expensemate.data.MessagePattern;
import com.example.expensemate.data.store.PatternStore;
import com.example.expensemate.domain.PatternService;

import java.util.List;
import java.util.concurrent.CompletableFuture;

final class LocalPatternService implements PatternService {
    private final PatternStore store;
    private final Writer writer;

    LocalPatternService(PatternStore store, Writer writer) {
        this.store = store;
        this.writer = writer;
    }

    @Override
    public LiveData<List<MessagePattern>> patterns() {
        return store.observeAll();
    }

    // Read each time rather than cached: a restore from backup writes the table directly
    @Override
    public List<MessagePattern> active() {
        return store.findActive();
    }

    @Override
    public CompletableFuture<Void> add(MessagePattern pattern) {
        return writer.run(() -> {
            pattern.setSortOrder(store.firstSortOrder() - 10);
            pattern.setBuiltinKey(null);
            store.insert(pattern);
        });
    }

    @Override
    public CompletableFuture<Void> update(MessagePattern pattern) {
        return writer.run(() -> store.update(pattern));
    }

    @Override
    public CompletableFuture<Void> reorder(List<Long> ids) {
        return writer.run(() -> {
            for (int i = 0; i < ids.size(); i++) {
                store.setSortOrder(ids.get(i), (i + 1) * 10);
            }
        });
    }

    @Override
    public CompletableFuture<Void> delete(MessagePattern pattern) {
        return writer.run(() -> store.delete(pattern));
    }
}
