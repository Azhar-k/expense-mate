package com.example.expensemate.ui.common;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.Menu;
import android.view.MenuItem;

import com.example.expensemate.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The order of the bottom tabs, as the person chose it in Settings. Until they choose, the tabs keep the
 * order in the menu file. Home stays the screen the app opens on, wherever it sits.
 */
public final class TabOrder {
    private static final String PREFS = "appearance";
    private static final String PREF_TABS = "tab_order";

    // Saved by name, so the choice survives tab ids changing between builds
    private static final int[] IDS = {
            R.id.navigation_home, R.id.navigation_money, R.id.navigation_activity, R.id.navigation_bills
    };
    private static final String[] KEYS = { "home", "money", "activity", "bills" };

    private TabOrder() {
    }

    /** The tab ids in the saved order. A tab missing from the saved order goes where the menu file puts it. */
    public static List<Integer> saved(Context context) {
        String saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(PREF_TABS, null);
        List<Integer> order = new ArrayList<>();
        if (saved != null) {
            for (String key : saved.split(",")) {
                int id = idOf(key.trim());
                if (id != 0 && !order.contains(id)) {
                    order.add(id);
                }
            }
        }
        for (int i = 0; i < IDS.length; i++) {
            if (!order.contains(IDS[i])) {
                order.add(Math.min(i, order.size()), IDS[i]);
            }
        }
        return order;
    }

    public static void save(Context context, List<Integer> ids) {
        StringBuilder keys = new StringBuilder();
        for (int id : ids) {
            String key = keyOf(id);
            if (key != null) {
                if (keys.length() > 0) {
                    keys.append(',');
                }
                keys.append(key);
            }
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(PREF_TABS, keys.toString()).apply();
    }

    /** Rebuilds the bar's menu in the saved order, keeping each tab's title and icon. */
    public static void apply(Context context, Menu menu) {
        Map<Integer, CharSequence> titles = new HashMap<>();
        Map<Integer, Drawable> icons = new HashMap<>();
        int checked = 0;
        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            titles.put(item.getItemId(), item.getTitle());
            icons.put(item.getItemId(), item.getIcon());
            if (item.isChecked()) {
                checked = item.getItemId();
            }
        }
        menu.clear();
        int position = 0;
        for (int id : saved(context)) {
            if (titles.containsKey(id)) {
                menu.add(Menu.NONE, id, position++, titles.get(id)).setIcon(icons.get(id)).setCheckable(true);
            }
        }
        if (checked != 0 && menu.findItem(checked) != null) {
            menu.findItem(checked).setChecked(true);
        }
    }

    /** The titles of the tabs in the saved order, for the Settings row. */
    public static String summary(Context context, Menu menu) {
        StringBuilder text = new StringBuilder();
        for (int id : saved(context)) {
            MenuItem item = menu.findItem(id);
            if (item != null) {
                if (text.length() > 0) {
                    text.append(" · ");
                }
                text.append(item.getTitle());
            }
        }
        return text.toString();
    }

    private static int idOf(String key) {
        for (int i = 0; i < KEYS.length; i++) {
            if (KEYS[i].equals(key)) {
                return IDS[i];
            }
        }
        return 0;
    }

    private static String keyOf(int id) {
        for (int i = 0; i < IDS.length; i++) {
            if (IDS[i] == id) {
                return KEYS[i];
            }
        }
        return null;
    }
}
