package com.example.expensemate.functional;

import androidx.annotation.ColorRes;
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
import android.view.View;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import androidx.test.espresso.matcher.RootMatchers;
import java.util.Locale;

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
    private static Matcher<View> withTextColor(final @ColorRes int colorRes) {
        return new BoundedMatcher<View, TextView>(TextView.class) {
            @Override
            public void describeTo(Description description) {
                description.appendText("with text color resource id: " + colorRes);
            }

            @Override
            protected boolean matchesSafely(TextView textView) {
                // resolve the @color resource into an actual ARGB value
                int expectedColor = ContextCompat.getColor(textView.getContext(), colorRes);
                return textView.getCurrentTextColor() == expectedColor;
            }
        };
    }

    @Test
    public void testDefaultMonthYearSelection() {
        // Wait for activity to be fully launched
        // No need for scenario.onActivity here, just use Espresso directly

        // Get current month and year
        Calendar calendar = Calendar.getInstance();
        String currentMonth = calendar.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault());
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
    public void testExpenseScreen_on_adding_transactions() {
        // Navigate to transactions screen
        Espresso.onView(ViewMatchers.withId(R.id.navigation_transactions))
            .perform(ViewActions.click());

        // Create an expense transaction
        Espresso.onView(ViewMatchers.withId(R.id.fabAddTransaction))
            .perform(ViewActions.click());
        
        Espresso.onView(ViewMatchers.withId(R.id.etAmount))
            .perform(ViewActions.typeText("100.00"));
        
        Espresso.onView(ViewMatchers.withId(R.id.etTransactionType))
            .perform(ViewActions.click());
        Espresso.onView(ViewMatchers.withText("DEBIT"))
            .perform(ViewActions.click());
        
        // Add transaction using dialog's positive button
        Espresso.onView(ViewMatchers.withText("Add"))
            .perform(ViewActions.click());

        // Navigate back to home screen

        // Verify expense amount is red
        Espresso.onView(ViewMatchers.withId(R.id.total_expense))
            .check(ViewAssertions.matches(withTextColor(R.color.debit_color)));

        // Verify totals are updated
        Espresso.onView(ViewMatchers.withId(R.id.total_expense))
                .check(ViewAssertions.matches(ViewMatchers.withText("₹100.00")));

        Espresso.onView(ViewMatchers.withId(R.id.total_balance))
                .check(ViewAssertions.matches(ViewMatchers.withText("-₹100.00")));

        // Navigate to transactions screen again
        Espresso.onView(ViewMatchers.withId(R.id.navigation_transactions))
            .perform(ViewActions.click());

        // Create an income transaction
        Espresso.onView(ViewMatchers.withId(R.id.fabAddTransaction))
            .perform(ViewActions.click());
        
        Espresso.onView(ViewMatchers.withId(R.id.etAmount))
            .perform(ViewActions.typeText("200.00"));
        
        // OPEN the transaction-type spinner/drop-down
        Espresso.onView(ViewMatchers.withId(R.id.etTransactionType))
            .perform(ViewActions.click());

        // SELECT the "CREDIT" item from the popup list
        Espresso.onData(Matchers.allOf(
                Matchers.instanceOf(String.class),
                Matchers.is("CREDIT")
            ))
            .inRoot(RootMatchers.isPlatformPopup())
            .perform(ViewActions.click());

        // Add transaction using dialog's positive button
        Espresso.onView(ViewMatchers.withText("Add"))
            .perform(ViewActions.click());

        // Navigate back to home screen


        // Verify income amount is green
        Espresso.onView(ViewMatchers.withId(R.id.total_income))
            .check(ViewAssertions.matches(withTextColor(R.color.credit_color)));

        // Verify totals are updated
        Espresso.onView(ViewMatchers.withId(R.id.total_income))
                .check(ViewAssertions.matches(ViewMatchers.withText("₹200.00")));

        Espresso.onView(ViewMatchers.withId(R.id.total_balance))
                .check(ViewAssertions.matches(ViewMatchers.withText("₹100.00")));
        Espresso.onView(ViewMatchers.withId(R.id.total_balance))
                .check(ViewAssertions.matches(withTextColor(R.color.credit_color)));

    }

    @Test
    public void testBottomNavigation() {
        // Verify home tab is selected by default


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


    }
} 