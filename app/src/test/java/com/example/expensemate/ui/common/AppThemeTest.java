package com.example.expensemate.ui.common;

import static org.junit.Assert.assertEquals;

import android.content.Context;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class AppThemeTest {
    private final Context context = ApplicationProvider.getApplicationContext();

    @After
    public void reset() {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }

    @Test
    public void followsThePhoneUntilSomethingIsChosen() {
        assertEquals(0, AppTheme.selected(context));
        AppTheme.apply(context);
        assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, AppCompatDelegate.getDefaultNightMode());
    }

    @Test
    public void lightAndDarkAreAppliedAndRemembered() {
        AppTheme.select(context, 1);
        assertEquals(AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.getDefaultNightMode());
        assertEquals(1, AppTheme.selected(context));

        AppTheme.select(context, 2);
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, AppCompatDelegate.getDefaultNightMode());

        // A restart starts from the saved choice, not from whatever was set last
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppTheme.apply(context);
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, AppCompatDelegate.getDefaultNightMode());
    }
}
