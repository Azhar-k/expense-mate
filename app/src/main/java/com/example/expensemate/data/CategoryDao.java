package com.example.expensemate.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

@Dao
public interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY name ASC")
    LiveData<List<Category>> getAllCategories();

    @Query("SELECT * FROM categories WHERE type = :type ORDER BY name ASC")
    LiveData<List<Category>> getCategoriesByType(String type);

    @Query("SELECT COUNT(*) FROM categories WHERE name = :name AND type = :type")
    int countCategories(String name, String type);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertCategory(Category category);

    @Query("UPDATE categories SET isDefault = 0 WHERE type = :type")
    void clearDefaultCategory(String type);

    @Query("UPDATE categories SET isDefault = 1 WHERE id = :id")
    void markDefaultCategory(long id);

    /** Saves the category and makes it the only default of its type. */
    @androidx.room.Transaction
    default void saveAsDefault(Category category) {
        if (category.getId() == 0) {
            category.setId(insertCategory(category));
        } else {
            updateCategory(category);
        }
        clearDefaultCategory(category.getType());
        markDefaultCategory(category.getId());
    }

    // Expense and income lists can share a name, such as Default and Others.
    @androidx.room.Transaction
    default void insertIfMissing(Category category) {
        if (countCategories(category.getName(), category.getType()) == 0) {
            insertCategory(category);
        }
    }

    @Update
    void updateCategory(Category category);

    @Delete
    void deleteCategory(Category category);

    @Query("SELECT * FROM categories ORDER BY name ASC")
    List<Category> getAllCategoriesSync();
}