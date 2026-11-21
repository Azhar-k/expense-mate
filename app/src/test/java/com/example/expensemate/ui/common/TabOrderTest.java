package com.example.expensemate.ui.common;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.widget.PopupMenu;

import androidx.test.core.app.ApplicationProvider;

import com.example.expensemate.R;
import com.example.expensemate.ui.accounts.MoneyFragment;

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
    public void keepsTheDefaultOrderUntilChosen() {
        assertEquals(Arrays.asList(R.id.navigation_home, R.id.navigation_money, R.id.navigation_activity,
                R.id.navigation_bills), TabOrder.BOTTOM.saved(context));
        assertEquals("Budgets · Funds · Accounts", MoneyFragment.SECTIONS.summary(context));
    }

    @Test
    public void savesAndRebuildsTheBarInTheChosenOrder() {
        List<Integer> chosen = Arrays.asList(R.id.navigation_bills, R.id.navigation_home, R.id.navigation_activity,
                R.id.navigation_money);
        TabOrder.BOTTOM.save(context, chosen);
        assertEquals(chosen, TabOrder.BOTTOM.saved(context));

        Menu menu = new PopupMenu(context, new View(context)).getMenu();
        new MenuInflater(context).inflate(R.menu.bottom_nav_menu, menu);
        TabOrder.BOTTOM.apply(context, menu);
        assertEquals(4, menu.size());
        for (int i = 0; i < chosen.size(); i++) {
            assertEquals((int) chosen.get(i), menu.getItem(i).getItemId());
        }
        assertEquals("Bills · Home · Activity · Money", TabOrder.BOTTOM.summary(context));
    }

    @Test
    public void theBarAndMoneyKeepSeparateOrders() {
        List<Integer> sections = MoneyFragment.SECTIONS.saved(context);
        MoneyFragment.SECTIONS.save(context, Arrays.asList(sections.get(2), sections.get(0), sections.get(1)));
        assertEquals("Accounts · Budgets · Funds", MoneyFragment.SECTIONS.summary(context));
        assertEquals("Home · Money · Activity · Bills", TabOrder.BOTTOM.summary(context));
    }

    @Test
    public void oneMissingFromTheSavedOrderKeepsItsDefaultPlace() {
        context.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit()
                .putString("tab_order", "bills,activity,unknown").commit();
        assertEquals(Arrays.asList(R.id.navigation_home, R.id.navigation_money, R.id.navigation_bills,
                R.id.navigation_activity), TabOrder.BOTTOM.saved(context));
    }
}
