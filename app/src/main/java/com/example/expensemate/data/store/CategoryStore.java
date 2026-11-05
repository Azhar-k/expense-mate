package com.example.expensemate.data.store;

import androidx.lifecycle.LiveData;

import com.example.expensemate.data.Category;

import java.util.List;

public interface CategoryStore {
    LiveData<List<Category>> observeAll();

    LiveData<List<Category>> observeByType(String type);

    /** @return the new row's id */
    long insert(Category category);

    void update(Category category);

    void delete(Category category);

    /** @return the name of the default category of this type, or null if there is none */
    String findDefaultName(String type);

    int lastSortOrder(String type);

    void setSortOrder(long id, int sortOrder);

    void clearDefault(String type);

    void setDefault(long id);
}
