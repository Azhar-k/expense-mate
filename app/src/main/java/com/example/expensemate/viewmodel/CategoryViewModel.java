package com.example.expensemate.viewmodel;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import com.example.expensemate.AppServices;
import com.example.expensemate.data.Category;
import com.example.expensemate.domain.CategoryService;
import java.util.List;

public class CategoryViewModel extends AndroidViewModel {
    private static final String TAG = "CategoryViewModel";
    private final CategoryService categories;

    public CategoryViewModel(Application application) {
        super(application);
        categories = AppServices.get(application).categories();
    }

    public LiveData<List<Category>> getAllCategories() {
        return categories.categories();
    }

    public LiveData<List<Category>> getCategoriesByType(String type) {
        return categories.byType(type);
    }

    public void insertCategory(Category category) {
        Writes.log(TAG, "adding category", categories.add(category));
    }

    public void saveOrder(List<Long> ids) {
        Writes.log(TAG, "reordering categories", categories.reorder(ids));
    }

    public void updateCategory(Category category) {
        Writes.log(TAG, "updating category", categories.update(category));
    }

    public void saveAsDefault(Category category) {
        Writes.log(TAG, "saving default category", categories.saveAsDefault(category));
    }

    public void deleteCategory(Category category) {
        Writes.log(TAG, "deleting category", categories.delete(category));
    }
}
