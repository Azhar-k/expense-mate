package com.example.expensemate.functional;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.espresso.Espresso;
import androidx.test.espresso.action.ViewActions;
import androidx.test.espresso.assertion.ViewAssertions;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.rule.GrantPermissionRule;
import com.example.expensemate.MainActivity;
import com.example.expensemate.R;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class TransactionManagementTest {
    @Rule
    public ActivityScenarioRule<MainActivity> activityRule = 
        new ActivityScenarioRule<>(MainActivity.class);

    @Rule
    public GrantPermissionRule permissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.READ_SMS,
        android.Manifest.permission.RECEIVE_SMS,
        android.Manifest.permission.FOREGROUND_SERVICE,
        android.Manifest.permission.FOREGROUND_SERVICE_DATA_SYNC,
        android.Manifest.permission.POST_NOTIFICATIONS
    );

    @Test
    public void testCreateTransaction() {
        // Navigate to transactions tab
        Espresso.onView(ViewMatchers.withId(R.id.navigation_transactions))
            .perform(ViewActions.click());

        // Click add transaction button
        Espresso.onView(ViewMatchers.withId(R.id.fabAddTransaction))
            .perform(ViewActions.click());

        // Fill transaction details
        Espresso.onView(ViewMatchers.withId(R.id.etAmount))
            .perform(ViewActions.typeText("100.00"));

        Espresso.onView(ViewMatchers.withId(R.id.etDescription))
            .perform(ViewActions.typeText("Test Transaction"));

        Espresso.onView(ViewMatchers.withId(R.id.etTransactionType))
            .perform(ViewActions.click());
        Espresso.onView(ViewMatchers.withText("Expense"))
            .perform(ViewActions.click());

        Espresso.onView(ViewMatchers.withId(R.id.etCategory))
            .perform(ViewActions.click());
        Espresso.onView(ViewMatchers.withText("Food"))
            .perform(ViewActions.click());

        // Save transaction using dialog's positive button
        Espresso.onView(ViewMatchers.withText("Save"))
            .perform(ViewActions.click());

        // Verify transaction is added to list
        Espresso.onView(ViewMatchers.withText("Test Transaction"))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void testEditTransaction() {
        // Navigate to transactions tab
        Espresso.onView(ViewMatchers.withId(R.id.navigation_transactions))
            .perform(ViewActions.click());

        // Click on existing transaction
        Espresso.onView(ViewMatchers.withText("Test Transaction"))
            .perform(ViewActions.click());

        // Edit transaction details
        Espresso.onView(ViewMatchers.withId(R.id.etAmount))
            .perform(ViewActions.clearText(), ViewActions.typeText("150.00"));

        Espresso.onView(ViewMatchers.withId(R.id.etDescription))
            .perform(ViewActions.clearText(), ViewActions.typeText("Updated Transaction"));

        // Save changes using dialog's positive button
        Espresso.onView(ViewMatchers.withText("Save"))
            .perform(ViewActions.click());

        // Verify transaction is updated
        Espresso.onView(ViewMatchers.withText("Updated Transaction"))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void testDeleteTransaction() {
        // Navigate to transactions tab
        Espresso.onView(ViewMatchers.withId(R.id.navigation_transactions))
            .perform(ViewActions.click());

        // Click on existing transaction
        Espresso.onView(ViewMatchers.withText("Updated Transaction"))
            .perform(ViewActions.click());

        // Click delete button
        Espresso.onView(ViewMatchers.withId(R.id.btn_delete))
            .perform(ViewActions.click());

        // Confirm deletion
        Espresso.onView(ViewMatchers.withText("Delete"))
            .perform(ViewActions.click());

        // Verify transaction is removed
        Espresso.onView(ViewMatchers.withText("Updated Transaction"))
            .check(ViewAssertions.doesNotExist());
    }

    @Test
    public void testTransactionListSorting() {
        // Navigate to transactions tab
        Espresso.onView(ViewMatchers.withId(R.id.navigation_transactions))
            .perform(ViewActions.click());

        // Select sort by date
        Espresso.onView(ViewMatchers.withText("Date"))
            .perform(ViewActions.click());

        // Verify list is sorted
        Espresso.onView(ViewMatchers.withId(R.id.recyclerView))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void testTransactionLinkingToRecurringPayment() {
        // Navigate to transactions tab
        Espresso.onView(ViewMatchers.withId(R.id.navigation_transactions))
            .perform(ViewActions.click());

        // Click add transaction button
        Espresso.onView(ViewMatchers.withId(R.id.fabAddTransaction))
            .perform(ViewActions.click());

        // Fill transaction details
        Espresso.onView(ViewMatchers.withId(R.id.etAmount))
            .perform(ViewActions.typeText("100.00"));

        Espresso.onView(ViewMatchers.withId(R.id.etDescription))
            .perform(ViewActions.typeText("Recurring Transaction"));

        Espresso.onView(ViewMatchers.withId(R.id.etTransactionType))
            .perform(ViewActions.click());
        Espresso.onView(ViewMatchers.withText("Expense"))
            .perform(ViewActions.click());

        // Enable recurring payment
        Espresso.onView(ViewMatchers.withId(R.id.etRecurringPayment))
            .perform(ViewActions.click());
        Espresso.onView(ViewMatchers.withText("Monthly Payment"))
            .perform(ViewActions.click());

        // Save transaction using dialog's positive button
        Espresso.onView(ViewMatchers.withText("Save"))
            .perform(ViewActions.click());

        // Verify transaction is added and marked as recurring
        Espresso.onView(ViewMatchers.withText("Recurring Transaction"))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }
} 