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
 * An order of tabs the person chose in Settings: the bottom bar, or the sections of a screen. Until they
 * choose, the tabs keep the order they were given in.
 */
public final class TabOrder {
    private static final String PREFS = "appearance";

    /** The bottom bar. Home stays the screen the app opens on, wherever it sits. */
    public static final TabOrder BOTTOM = new TabOrder("tab_order",
            new int[] { R.id.navigation_home, R.id.navigation_money, R.id.navigation_activity, R.id.navigation_bills },
            new String[] { "home", "money", "activity", "bills" },
            new String[] { "Home", "Money", "Activity", "Bills" });

    private final String pref;
    private final int[] ids;
    // Saved by name, so the choice survives ids changing between builds
    private final String[] keys;
    private final String[] labels;

    public TabOrder(String pref, int[] ids, String[] keys, String[] labels) {
        this.pref = pref;
        this.ids = ids;
        this.keys = keys;
        this.labels = labels;
    }

    /** The ids in the saved order. One missing from the saved order goes where the default order puts it. */
    public List<Integer> saved(Context context) {
        String saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(pref, null);
        List<Integer> order = new ArrayList<>();
        if (saved != null) {
            for (String key : saved.split(",")) {
                int index = indexOfKey(key.trim());
                if (index >= 0 && !order.contains(ids[index])) {
                    order.add(ids[index]);
                }
            }
        }
        for (int i = 0; i < ids.length; i++) {
            if (!order.contains(ids[i])) {
                order.add(Math.min(i, order.size()), ids[i]);
            }
        }
        return order;
    }

    public void save(Context context, List<Integer> order) {
        StringBuilder text = new StringBuilder();
        for (int id : order) {
            int index = indexOfId(id);
            if (index >= 0) {
                if (text.length() > 0) {
                    text.append(',');
                }
                text.append(keys[index]);
            }
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(pref, text.toString()).apply();
    }

    public String label(int id) {
        int index = indexOfId(id);
        return index >= 0 ? labels[index] : "";
    }

    /** The labels in the saved order. */
    public List<String> labels(Context context) {
        List<String> names = new ArrayList<>();
        for (int id : saved(context)) {
            names.add(label(id));
        }
        return names;
    }

    /** The labels in the saved order, for a Settings row. */
    public String summary(Context context) {
        return String.join(" · ", labels(context));
    }

    /** Rebuilds a menu in the saved order, keeping each item's title and icon. */
    public void apply(Context context, Menu menu) {
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

    private int indexOfKey(String key) {
        for (int i = 0; i < keys.length; i++) {
            if (keys[i].equals(key)) {
                return i;
            }
        }
        return -1;
    }

    private int indexOfId(int id) {
        for (int i = 0; i < ids.length; i++) {
            if (ids[i] == id) {
                return i;
            }
        }
        return -1;
    }
}
