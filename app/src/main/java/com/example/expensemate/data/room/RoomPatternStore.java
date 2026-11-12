package com.example.expensemate.data.room;

import androidx.lifecycle.LiveData;

import com.example.expensemate.data.MessagePattern;
import com.example.expensemate.data.MessagePatternDao;
import com.example.expensemate.data.store.PatternStore;

import java.util.List;

final class RoomPatternStore implements PatternStore {
    private final MessagePatternDao dao;

    RoomPatternStore(MessagePatternDao dao) {
        this.dao = dao;
    }

    @Override
    public LiveData<List<MessagePattern>> observeAll() {
        return dao.observeAll();
    }

    @Override
    public List<MessagePattern> findActive() {
        return dao.getActiveSync();
    }

    @Override
    public long insert(MessagePattern pattern) {
        return dao.insert(pattern);
    }

    @Override
    public void update(MessagePattern pattern) {
        dao.update(pattern);
    }

    @Override
    public void delete(MessagePattern pattern) {
        dao.delete(pattern);
    }

    @Override
    public int firstSortOrder() {
        return dao.firstSortOrder();
    }

    @Override
    public void setSortOrder(long id, int sortOrder) {
        dao.setSortOrder(id, sortOrder);
    }
}
