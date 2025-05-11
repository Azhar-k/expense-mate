package com.example.expensemate.functional;

import static org.hamcrest.CoreMatchers.containsString;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.espresso.Espresso;
import androidx.test.espresso.action.ViewActions;
import androidx.test.espresso.assertion.ViewAssertions;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.rule.GrantPermissionRule;
import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.IdlingRegistry;
import androidx.test.espresso.IdlingResource;
import com.example.expensemate.MainActivity;
import com.example.expensemate.R;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.Calendar;
import java.util.Date;
import android.Manifest;
import androidx.test.espresso.contrib.DrawerActions;

@RunWith(AndroidJUnit4.class)
public class RecurringPaymentTest {
    private ActivityScenario<MainActivity> scenario;
    private IdlingResource idlingResource;

    @Rule
    public GrantPermissionRule permissionRule = GrantPermissionRule.grant(
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.FOREGROUND_SERVICE_DATA_SYNC
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
    public void testCreateRecurringPayment() {
        // Open navigation drawer by clicking the hamburger menu
        Espresso.onView(ViewMatchers.withId(R.id.drawer_layout))
            .perform(DrawerActions.open());

        // Navigate to recurring payments screen
        Espresso.onView(ViewMatchers.withId(R.id.nav_recurring_payments))
            .perform(ViewActions.click());

        // Click add button
        Espresso.onView(ViewMatchers.withId(R.id.fab_add_recurring_payment))
            .perform(ViewActions.click());

        // Fill in payment details
        Espresso.onView(ViewMatchers.withId(R.id.etPaymentName))
            .perform(ViewActions.typeText("Rent Payment"));

        Espresso.onView(ViewMatchers.withId(R.id.etAmount))
            .perform(ViewActions.typeText("1000.00"));

        Espresso.onView(ViewMatchers.withId(R.id.etDueDate))
            .perform(ViewActions.typeText("1"));

        
        // Click on expiry date field to open date picker
        Espresso.onView(ViewMatchers.withId(R.id.etExpiryDate))
            .perform(ViewActions.click());

        // Select the date (e.g., May 30, 2025)
        Espresso.onView(ViewMatchers.withText(containsString("2")))
                .perform(ViewActions.click()); // Select the day
        Espresso.onView(ViewMatchers.withText("OK")) // Confirm the date selection
                .perform(ViewActions.click());

        // Save the payment
        Espresso.onView(ViewMatchers.withText("Add"))
            .perform(ViewActions.click());

        // Verify payment appears in list
        Espresso.onView(ViewMatchers.withText("Rent Payment"))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));

        // Verify amount is displayed
        Espresso.onView(ViewMatchers.withText("₹1000.00"))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));

        // Verify due date is displayed
        Espresso.onView(ViewMatchers.withText(containsString("Day 1 of every month")))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void testEditRecurringPayment() {
        // First create a payment to edit
        testCreateRecurringPayment();

        // Click edit button on the payment
        Espresso.onView(ViewMatchers.withId(R.id.btn_edit))
            .perform(ViewActions.click());

        // Edit payment details
        Espresso.onView(ViewMatchers.withId(R.id.etPaymentName))
            .perform(ViewActions.clearText())
            .perform(ViewActions.typeText("Updated Rent Payment"));

        Espresso.onView(ViewMatchers.withId(R.id.etAmount))
            .perform(ViewActions.clearText())
            .perform(ViewActions.typeText("1200.00"));

        Espresso.onView(ViewMatchers.withId(R.id.etDueDate))
            .perform(ViewActions.clearText())
            .perform(ViewActions.typeText("20"));

        // Save the changes
        Espresso.onView(ViewMatchers.withText("Add"))
            .perform(ViewActions.click());

        // Verify updated payment appears in list
        Espresso.onView(ViewMatchers.withText(containsString("Updated Rent Payment")))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));

        // Verify updated amount is displayed
        Espresso.onView(ViewMatchers.withText(containsString("₹1200.00")))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));

        // Verify updated due date is displayed
        Espresso.onView(ViewMatchers.withText(containsString("Day 20 of every month")))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void testMarkPaymentAsComplete() {
        // First create a payment
        testCreateRecurringPayment();

        // Click the checkbox to mark as complete
        Espresso.onView(ViewMatchers.withId(R.id.payment_completed))
            .perform(ViewActions.click());

        // Verify checkbox is checked
        Espresso.onView(ViewMatchers.withId(R.id.payment_completed))
            .check(ViewAssertions.matches(ViewMatchers.isChecked()));

        // Verify remaining amount is updated
        Espresso.onView(ViewMatchers.withId(R.id.tv_remaining_amount))
            .check(ViewAssertions.matches(ViewMatchers.withText(containsString("Remaining: ₹0.00"))));

        // Uncheck to mark as incomplete
        Espresso.onView(ViewMatchers.withId(R.id.payment_completed))
            .perform(ViewActions.click());

        // Verify checkbox is unchecked
        Espresso.onView(ViewMatchers.withId(R.id.payment_completed))
            .check(ViewAssertions.matches(ViewMatchers.isNotChecked()));

        // Verify remaining amount is updated back
        Espresso.onView(ViewMatchers.withId(R.id.tv_remaining_amount))
            .check(ViewAssertions.matches(ViewMatchers.withText(containsString("Remaining: ₹1200.00"))));
    }
} 