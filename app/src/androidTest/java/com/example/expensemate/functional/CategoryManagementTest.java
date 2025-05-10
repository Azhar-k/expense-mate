package com.example.expensemate.functional;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.expensemate.MainActivity;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CategoryManagementTest {
    @Rule
    public ActivityScenarioRule<MainActivity> activityRule = 
        new ActivityScenarioRule<>(MainActivity.class);

    @Test
    public void testCreateCategory() {
        // Test creating a new category
        // Verify category appears in list
        // Verify category can be selected for transactions
    }

    @Test
    public void testEditCategory() {
        // Test editing an existing category
        // Verify changes are reflected in the list
        // Verify changes are reflected in transactions using this category
    }

    @Test
    public void testDeleteCategory() {
        // Test deleting a category
        // Verify category is removed from list
        // Verify transactions using this category are updated to "Others"
    }

    @Test
    public void testDefaultCategory() {
        // Test "Others" category exists by default
        // Verify it cannot be deleted
        // Verify it is assigned to new transactions by default
    }

    @Test
    public void testCategoryNavigation() {
        // Test navigation to category screen from left menu
        // Verify bottom menu is hidden
    }
} 