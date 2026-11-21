package com.example.expensemate.ui.common;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.view.Menu;
import android.widget.PopupMenu;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;

import com.example.expensemate.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class TabOrderTest {
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit().clear().commit();
    }

    @Test
    public void keepsTheMenuOrderUntilChosen() {
        assertEquals(Arrays.asList(R.id.navigation_home, R.id.navigation_money, R.id.navigation_activity,
                R.id.navigation_bills), TabOrder.saved(context));
    }

    @Test
    public void savesAndRebuildsTheMenuInTheChosenOrder() {
        List<Integer> chosen = Arrays.asList(R.id.navigation_bills, R.id.navigation_home, R.id.navigation_activity,
                R.id.navigation_money);
        TabOrder.save(context, chosen);
        assertEquals(chosen, TabOrder.saved(context));

        Menu menu = new PopupMenu(context, new View(context)).getMenu();
        new android.view.MenuInflater(context).inflate(R.menu.bottom_nav_menu, menu);
        TabOrder.apply(context, menu);
        assertEquals(4, menu.size());
        for (int i = 0; i < chosen.size(); i++) {
            assertEquals((int) chosen.get(i), menu.getItem(i).getItemId());
        }
        assertEquals("Bills · Home · Activity · Money", TabOrder.summary(context, menu));
    }

    @Test
    public void aTabMissingFromTheSavedOrderKeepsItsMenuPlace() {
        context.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit()
                .putString("tab_order", "bills,activity,unknown").commit();
        assertEquals(Arrays.asList(R.id.navigation_home, R.id.navigation_money, R.id.navigation_bills,
                R.id.navigation_activity), TabOrder.saved(context));
    }
}
