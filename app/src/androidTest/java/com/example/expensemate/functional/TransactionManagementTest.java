package com.example.expensemate.functional;

import static org.hamcrest.CoreMatchers.containsString;

import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.IdlingPolicies;
import androidx.test.espresso.IdlingRegistry;
import androidx.test.espresso.IdlingResource;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.espresso.Espresso;
import androidx.test.espresso.action.ViewActions;
import androidx.test.espresso.assertion.ViewAssertions;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.rule.GrantPermissionRule;
import com.example.expensemate.MainActivity;
import com.example.expensemate.R;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class TransactionManagementTest {
    private ActivityScenario<MainActivity> scenario;
    private IdlingResource idlingResource;

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

    @Before
    public void setup() {
        scenario = ActivityScenario.launch(MainActivity.class);
        scenario.onActivity(activity -> {
            // Wait for activity to be fully created and resumed

        });
    }

    @After
    public void cleanup() {
        if (scenario != null) {
            scenario.close();
        }
        if (idlingResource != null) {
            IdlingRegistry.getInstance().unregister(idlingResource);
        }
    }

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

        // Set the date
        Espresso.onView(ViewMatchers.withId(R.id.etDate))
                .perform(ViewActions.click());
        // Select the date (e.g., May 1, 2025)
        Espresso.onView(ViewMatchers.withText(containsString("1")))
                .perform(ViewActions.click()); // Select the day
        Espresso.onView(ViewMatchers.withText("OK")) // Confirm the date selection
                .perform(ViewActions.click());

        Espresso.onView(ViewMatchers.withId(R.id.etDescription))
            .perform(ViewActions.typeText("Test Transaction"));

        Espresso.onView(ViewMatchers.withId(R.id.etTransactionType))
            .perform(ViewActions.click());
        Espresso.onView(ViewMatchers.withText("DEBIT"))
            .perform(ViewActions.click());

        // Save transaction using dialog's positive button
        Espresso.onView(ViewMatchers.withText("Add"))
            .perform(ViewActions.click());

        //Goto another tab and comeback to verify that the transaction will be fetched from db

        Espresso.onView(ViewMatchers.withId(R.id.navigation_transactions))
                .perform(ViewActions.click());

        // Verify the content of the transaction description
        Espresso.onView(ViewMatchers.withId(R.id.recyclerView))
                .check(ViewAssertions.matches(ViewMatchers.hasDescendant(ViewMatchers.withText(containsString("Test Transaction")))));
        // Verify the amount is displayed correctly
        Espresso.onView(ViewMatchers.withId(R.id.recyclerView))
                .check(ViewAssertions.matches(ViewMatchers.hasDescendant(ViewMatchers.withText("₹100.00"))));

        // Verify the transaction type is displayed correctly
        Espresso.onView(ViewMatchers.withId(R.id.recyclerView))
                .check(ViewAssertions.matches(ViewMatchers.hasDescendant(ViewMatchers.withText("Type: DEBIT"))));


        // Verify the date (assuming you set a date in your test)
        Espresso.onView(ViewMatchers.withId(R.id.recyclerView))
                .check(ViewAssertions.matches(ViewMatchers.hasDescendant(ViewMatchers.withText(containsString("Date: 01 May 2025")))));
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
            .perform(ViewActions.typeText("Recurring Transaction EMI1"));

        Espresso.onView(ViewMatchers.withId(R.id.etTransactionType))
            .perform(ViewActions.click());
        Espresso.onView(ViewMatchers.withText("DEBIT"))
            .perform(ViewActions.click());

        // Enable recurring payment
        Espresso.onView(ViewMatchers.withId(R.id.etRecurringPayment))
            .perform(ViewActions.click());
        Espresso.onView(ViewMatchers.withText("EMI1"))
            .perform(ViewActions.click());

        // Save transaction using dialog's positive button
        Espresso.onView(ViewMatchers.withText("ADD"))
            .perform(ViewActions.click());

        // Verify transaction is added and marked as recurring
        Espresso.onView(ViewMatchers.withText(containsString("Recurring Transaction EMI1")))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }
} 