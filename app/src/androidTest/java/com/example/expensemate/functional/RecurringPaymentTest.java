package com.example.expensemate.functional;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.expensemate.MainActivity;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class RecurringPaymentTest {
    @Rule
    public ActivityScenarioRule<MainActivity> activityRule = 
        new ActivityScenarioRule<>(MainActivity.class);

    @Test
    public void testCreateRecurringPayment() {
        // Test creating a new recurring payment
        // Verify payment appears in list
        // Verify sorting by completion status and due date
    }

    @Test
    public void testEditRecurringPayment() {
        // Test editing an existing recurring payment
        // Verify changes are reflected in the list
        // Verify linked transactions are updated if needed
    }

    @Test
    public void testDeleteRecurringPayment() {
        // Test deleting a recurring payment
        // Verify payment is removed from list
        // Verify linked transactions are unlinked
    }

    @Test
    public void testExpiredPaymentHighlighting() {
        // Test expired payment is highlighted in yellow
        // Test expiry date is shown in red
    }

    @Test
    public void testMarkAllAsCompleted() {
        // Test "Mark All as Completed" button
        // Verify all payments are marked as completed
        // Verify totals are updated
    }

    @Test
    public void testMarkAllAsUncompleted() {
        // Test "Mark All as Uncompleted" button
        // Verify all payments are marked as uncompleted
        // Verify totals are updated
    }

    @Test
    public void testTotalAmountCalculation() {
        // Test total amount calculation
        // Test remaining amount calculation
        // Verify amounts are shown at top left
    }

    @Test
    public void testRecurringPaymentNavigation() {
        // Test navigation to recurring payment screen from left menu
        // Verify bottom menu is hidden
    }
} 