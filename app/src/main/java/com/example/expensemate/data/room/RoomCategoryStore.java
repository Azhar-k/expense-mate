package com.example.expensemate.data.room;

import androidx.lifecycle.LiveData;

import com.example.expensemate.data.Category;
import com.example.expensemate.data.CategoryDao;
import com.example.expensemate.data.store.CategoryStore;

import java.util.List;

final class RoomCategoryStore implements CategoryStore {
    private final CategoryDao dao;

    RoomCategoryStore(CategoryDao dao) {
        this.dao = dao;
    }

    @Override
    public LiveData<List<Category>> observeAll() {
        return dao.getAllCategories();
    }

    @Override
    public LiveData<List<Category>> observeByType(String type) {
        return dao.getCategoriesByType(type);
    }

    @Override
    public long insert(Category category) {
        return dao.insertCategory(category);
    }

    @Override
    public void update(Category category) {
        dao.updateCategory(category);
    }

    @Override
    public void delete(Category category) {
        dao.deleteCategory(category);
    }

    @Override
    public String findDefaultName(String type) {
        return dao.getDefaultName(type);
    }

    @Override
    public int lastSortOrder(String type) {
        return dao.lastSortOrder(type);
    }

    @Override
    public void setSortOrder(long id, int sortOrder) {
        dao.setSortOrder(id, sortOrder);
    }

    @Override
    public void clearDefault(String type) {
        dao.clearDefaultCategory(type);
    }

    @Override
    public void setDefault(long id) {
        dao.markDefaultCategory(id);
    }
}
