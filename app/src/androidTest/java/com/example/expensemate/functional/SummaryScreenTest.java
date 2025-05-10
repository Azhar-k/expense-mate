package com.example.expensemate.functional;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.expensemate.MainActivity;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SummaryScreenTest {
    @Rule
    public ActivityScenarioRule<MainActivity> activityRule = 
        new ActivityScenarioRule<>(MainActivity.class);

    @Test
    public void testDefaultMonthYearSelection() {
        // Test that current month and year are selected by default
        // Verify category-wise expenses are shown for current month
    }

    @Test
    public void testMonthFiltering() {
        // Test filtering by different month
        // Verify category-wise expenses update correctly
        // Verify transactions shown are from selected period
    }

    @Test
    public void testCategoryWiseExpenseCalculation() {
        // Test expense calculation for each category
        // Verify linked recurring transactions are excluded
        // Verify total matches sum of category expenses
    }

    @Test
    public void testAmountColors() {
        // Test all amounts are shown in red (expenses)
    }

    @Test
    public void testEmptyCategories() {
        // Test behavior when no transactions exist for a category
        // Verify category is shown with zero amount
    }
} 