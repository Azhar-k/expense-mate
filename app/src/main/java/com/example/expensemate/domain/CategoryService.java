package com.example.expensemate.domain;

import androidx.lifecycle.LiveData;

import com.example.expensemate.data.Category;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface CategoryService {
    LiveData<List<Category>> categories();

    LiveData<List<Category>> byType(String type);

    /** A new category goes to the end of a list the user has ordered, or into the alphabetical one. */
    CompletableFuture<Void> add(Category category);

    CompletableFuture<Void> update(Category category);

    /** Numbers the categories from one in the order given. */
    CompletableFuture<Void> reorder(List<Long> ids);

    /** Saves the category, new or not, and makes it the only default of its type. */
    CompletableFuture<Void> saveAsDefault(Category category);

    CompletableFuture<Void> delete(Category category);
}
