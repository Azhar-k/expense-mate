package com.example.expensemate.ui.common;

import android.content.Context;

import androidx.appcompat.app.AppCompatDelegate;

/** The theme the person chose in Settings. Until they choose, the app follows the phone. */
public final class AppTheme {
    public static final String[] LABELS = { "Follow phone", "Light", "Dark" };

    private static final int[] MODES = {
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
            AppCompatDelegate.MODE_NIGHT_NO,
            AppCompatDelegate.MODE_NIGHT_YES
    };
    private static final String PREFS = "appearance";
    private static final String PREF_THEME = "theme";

    private AppTheme() {
    }

    /** @return the position of the saved choice in {@link #LABELS} */
    public static int selected(Context context) {
        int saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(PREF_THEME, 0);
        return saved >= 0 && saved < MODES.length ? saved : 0;
    }

    /** Call before the activity is created, so it never opens in the wrong theme. */
    public static void apply(Context context) {
        AppCompatDelegate.setDefaultNightMode(MODES[selected(context)]);
    }

    /** Saves the choice and switches now. Android redraws the open screen in the new theme. */
    public static void select(Context context, int position) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(PREF_THEME, position).apply();
        apply(context);
    }
}
