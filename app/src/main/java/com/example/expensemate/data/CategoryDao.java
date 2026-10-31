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
    void insertCategory(Category category);

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