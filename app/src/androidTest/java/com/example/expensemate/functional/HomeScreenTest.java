package com.example.expensemate.functional;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.espresso.Espresso;
import androidx.test.espresso.action.ViewActions;
import androidx.test.espresso.assertion.ViewAssertions;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.espresso.matcher.BoundedMatcher;
import androidx.test.rule.GrantPermissionRule;
import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.IdlingRegistry;
import androidx.test.espresso.IdlingResource;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import com.example.expensemate.MainActivity;
import com.example.expensemate.R;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.Calendar;
import android.Manifest;
import android.graphics.Color;
import android.view.View;
import android.widget.TextView;

@RunWith(AndroidJUnit4.class)
public class HomeScreenTest {
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
            while (!activity.isFinishing() && !activity.isDestroyed()) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
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

    // Custom matcher for text color
    private static Matcher<View> withTextColor(final int color) {
        return new BoundedMatcher<View, TextView>(TextView.class) {
            @Override
            public void describeTo(Description description) {
                description.appendText("with text color: " + color);
            }

            @Override
            public boolean matchesSafely(TextView textView) {
                return textView.getCurrentTextColor() == color;
            }
        };
    }

    @Test
    public void  testDefaultMonthYearSelection() {
        // Wait for activity to be fully launched
        scenario.onActivity(activity -> {
            // Get current month and year
            Calendar calendar = Calendar.getInstance();
            String currentMonth = String.format("%02d", calendar.get(Calendar.MONTH) + 1);
            String currentYear = String.valueOf(calendar.get(Calendar.YEAR));

            // Verify period text shows current month and year
            Espresso.onView(ViewMatchers.withId(R.id.tv_period))
                .check(ViewAssertions.matches(ViewMatchers.withText(Matchers.containsString(currentMonth + " " + currentYear))));

            // Verify totals are shown
            Espresso.onView(ViewMatchers.withId(R.id.total_income))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
            
            Espresso.onView(ViewMatchers.withId(R.id.total_expense))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
            
            Espresso.onView(ViewMatchers.withId(R.id.total_balance))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
        });
    }

    @Test
    public void testMonthYearFiltering() {
        // Click previous month button
        Espresso.onView(ViewMatchers.withId(R.id.btn_prev_month))
            .perform(ViewActions.click());

        // Verify totals are updated
        Espresso.onView(ViewMatchers.withId(R.id.total_income))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
        
        Espresso.onView(ViewMatchers.withId(R.id.total_expense))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
        
        Espresso.onView(ViewMatchers.withId(R.id.total_balance))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void testTotalCalculations() {
        // Create a test transaction
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
        
        // Save transaction using dialog's positive button
        Espresso.onView(ViewMatchers.withText("Save"))
            .perform(ViewActions.click());

        // Verify totals are updated
        Espresso.onView(ViewMatchers.withId(R.id.total_expense))
            .check(ViewAssertions.matches(ViewMatchers.withText("₹100.00")));
        
        Espresso.onView(ViewMatchers.withId(R.id.total_balance))
            .check(ViewAssertions.matches(ViewMatchers.withText("-₹100.00")));
    }

    @Test
    public void testAmountColors() {
        // Create an expense transaction
        Espresso.onView(ViewMatchers.withId(R.id.fabAddTransaction))
            .perform(ViewActions.click());
        
        Espresso.onView(ViewMatchers.withId(R.id.etAmount))
            .perform(ViewActions.typeText("100.00"));
        
        Espresso.onView(ViewMatchers.withId(R.id.etTransactionType))
            .perform(ViewActions.click());
        Espresso.onView(ViewMatchers.withText("Expense"))
            .perform(ViewActions.click());
        
        // Save transaction using dialog's positive button
        Espresso.onView(ViewMatchers.withText("Save"))
            .perform(ViewActions.click());

        // Verify expense amount is red
        Espresso.onView(ViewMatchers.withId(R.id.total_expense))
            .check(ViewAssertions.matches(withTextColor(Color.RED)));

        // Create an income transaction
        Espresso.onView(ViewMatchers.withId(R.id.fabAddTransaction))
            .perform(ViewActions.click());
        
        Espresso.onView(ViewMatchers.withId(R.id.etAmount))
            .perform(ViewActions.typeText("200.00"));
        
        Espresso.onView(ViewMatchers.withId(R.id.etTransactionType))
            .perform(ViewActions.click());
        Espresso.onView(ViewMatchers.withText("Income"))
            .perform(ViewActions.click());
        
        // Save transaction using dialog's positive button
        Espresso.onView(ViewMatchers.withText("Save"))
            .perform(ViewActions.click());

        // Verify income amount is green
        Espresso.onView(ViewMatchers.withId(R.id.total_income))
            .check(ViewAssertions.matches(withTextColor(Color.GREEN)));
    }

    @Test
    public void testBottomNavigation() {
        // Verify home tab is selected by default
        Espresso.onView(ViewMatchers.withId(R.id.navigation_expense))
            .check(ViewAssertions.matches(ViewMatchers.isSelected()));

        // Navigate to summary tab
        Espresso.onView(ViewMatchers.withId(R.id.navigation_summary))
            .perform(ViewActions.click());
        Espresso.onView(ViewMatchers.withId(R.id.navigation_summary))
            .check(ViewAssertions.matches(ViewMatchers.isSelected()));

        // Navigate to transactions tab
        Espresso.onView(ViewMatchers.withId(R.id.navigation_transactions))
            .perform(ViewActions.click());
        Espresso.onView(ViewMatchers.withId(R.id.navigation_transactions))
            .check(ViewAssertions.matches(ViewMatchers.isSelected()));

        // Navigate back to home tab
        Espresso.onView(ViewMatchers.withId(R.id.navigation_expense))
            .perform(ViewActions.click());
        Espresso.onView(ViewMatchers.withId(R.id.navigation_expense))
            .check(ViewAssertions.matches(ViewMatchers.isSelected()));
    }
} 