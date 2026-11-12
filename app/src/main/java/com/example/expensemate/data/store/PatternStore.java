package com.example.expensemate.data.store;

import androidx.lifecycle.LiveData;

import com.example.expensemate.data.MessagePattern;

import java.util.List;

public interface PatternStore {
    /** Every pattern, in the order they are tried. */
    LiveData<List<MessagePattern>> observeAll();

    /** The patterns that are on, in the order they are tried. */
    List<MessagePattern> findActive();

    /** @return the new row's id */
    long insert(MessagePattern pattern);

    void update(MessagePattern pattern);

    void delete(MessagePattern pattern);

    int firstSortOrder();

    void setSortOrder(long id, int sortOrder);
}
