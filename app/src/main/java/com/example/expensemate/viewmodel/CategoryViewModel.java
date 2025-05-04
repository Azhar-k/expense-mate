package com.example.expensemate.viewmodel;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import com.example.expensemate.data.AppDatabase;
import com.example.expensemate.data.Category;
import com.example.expensemate.data.CategoryDao;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CategoryViewModel extends AndroidViewModel {
    private final CategoryDao categoryDao;
    private final ExecutorService executorService;
    private final LiveData<List<Category>> allCategories;

    public CategoryViewModel(Application application) {
        super(application);
        AppDatabase database = AppDatabase.getDatabase(application);
        categoryDao = database.categoryDao();
        executorService = Executors.newSingleThreadExecutor();
        allCategories = categoryDao.getAllCategories();
    }

    public LiveData<List<Category>> getAllCategories() {
        return allCategories;
    }

    public LiveData<List<Category>> getCategoriesByType(String type) {
        return categoryDao.getCategoriesByType(type);
    }

    public void insertCategory(Category category) {
        executorService.execute(() -> categoryDao.insertCategory(category));
    }

    public void updateCategory(Category category) {
        executorService.execute(() -> categoryDao.updateCategory(category));
    }

    public void deleteCategory(Category category) {
        executorService.execute(() -> categoryDao.deleteCategory(category));
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executorService.shutdown();
    }
} 