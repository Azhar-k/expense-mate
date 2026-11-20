package com.example.expensemate.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.example.expensemate.domain.BudgetService;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class BudgetDaoTest {
    private static final long EVERYDAY = 1;
    private static final long TRIP = 2;
    private static final long BANK = 1; // The same id as a fund, which must not matter

    private AppDatabase database;
    private BudgetService service;
    private long nextId = 1;
    private Date monthStart;
    private Date monthEnd;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).allowMainThreadQueries().build();
        service = TestServices.over(database).budgets();
        fund(EVERYDAY, "Everyday", true);
        fund(TRIP, "Trip", false);
        Account bank = new Account("Bank", "", "", null, "");
        bank.setId(BANK);
        bank.setDefault(true);
        bank.setCurrency("INR");
        database.accountDao().insert(bank);

        Calendar start = Calendar.getInstance();
        start.set(2026, Calendar.NOVEMBER, 1, 0, 0, 0);
        start.set(Calendar.MILLISECOND, 0);
        monthStart = start.getTime();
        Calendar end = (Calendar) start.clone();
        end.add(Calendar.MONTH, 1);
        end.add(Calendar.MILLISECOND, -1);
        monthEnd = end.getTime();
    }

    @After
    public void tearDown() {
        database.close();
    }

    private void fund(long id, String name, boolean isDefault) {
        Fund fund = new Fund(name, "");
        fund.setId(id);
        fund.setDefault(isDefault);
        database.fundDao().insert(fund);
    }

    private void spend(double amount, String category, long fund, Date date) {
        spend(amount, category, fund, date, row -> { });
    }

    private void spend(double amount, String category, long fund, Date date,
            java.util.function.Consumer<Transaction> change) {
        Transaction row = new Transaction();
        row.setId(nextId++);
        row.setTransactionType("DEBIT");
        row.setAmount(amount);
        row.setCategory(category);
        row.setFundId(fund);
        row.setAccountId(BANK);
        row.setCurrency("INR");
        row.setDate(date);
        change.accept(row);
        database.transactionDao().insertTransaction(row);
    }

    private Date inMonth(int day) {
        Calendar when = Calendar.getInstance();
        when.setTime(monthStart);
        when.set(Calendar.DAY_OF_MONTH, day);
        when.set(Calendar.HOUR_OF_DAY, 12);
        return when.getTime();
    }

    private long addBudget(String name, double amount, String currency, String... categories) {
        Budget budget = new Budget();
        budget.setName(name);
        budget.setAmount(amount);
        budget.setCurrency(currency);
        return service.add(budget, Arrays.asList(categories)).join();
    }

    private BudgetProgress only() {
        List<BudgetProgress> all = database.budgetDao().getProgressSync(monthStart, monthEnd);
        assertEquals(1, all.size());
        return all.get(0);
    }

    @Test
    public void countsEverydaySpendingInItsCategoriesThisMonth() {
        addBudget("Eating", 1000, "INR", "Food", "Outside Food");
        spend(300, "Food", EVERYDAY, inMonth(2));
        spend(250, "Outside Food", EVERYDAY, inMonth(28));
        spend(999, "Fuel", EVERYDAY, inMonth(5)); // Another category

        BudgetProgress progress = only();
        assertEquals(550, progress.spent, 0.001);
        assertEquals(450, progress.left(), 0.001);
        assertEquals(55, progress.percent());
        assertFalse(progress.isOver());
        assertEquals(Arrays.asList("Food", "Outside Food"), progress.categories());
    }

    @Test
    public void leavesOutOtherFundsMonthsTransfersExclusionsAndCurrencies() {
        addBudget("Eating", 1000, "INR", "Food");
        spend(100, "Food", EVERYDAY, inMonth(10)); // The only one that counts

        spend(500, "Food", TRIP, inMonth(10)); // Set aside already
        spend(500, "Food", EVERYDAY, new Date(monthStart.getTime() - 1)); // Last month
        spend(500, "Food", EVERYDAY, new Date(monthEnd.getTime() + 1)); // Next month
        spend(500, "Food", EVERYDAY, inMonth(10), row -> row.setTransferGroupId("t1"));
        spend(500, "Food", EVERYDAY, inMonth(10), row -> row.setExcludedFromSummary(true));
        spend(500, "Food", EVERYDAY, inMonth(10), row -> row.setCurrency("AED"));
        spend(500, "Food", EVERYDAY, inMonth(10), row -> row.setTransactionType("CREDIT"));

        assertEquals(100, only().spent, 0.001);
    }

    @Test
    public void followsTheDefaultFundWhenItChanges() {
        addBudget("Eating", 1000, "INR", "Food");
        spend(100, "Food", EVERYDAY, inMonth(3));
        spend(700, "Food", TRIP, inMonth(3));

        database.fundDao().clearDefaultFund();
        database.fundDao().setDefaultFund(TRIP);

        assertEquals(700, only().spent, 0.001);
    }

    @Test
    public void goingOverShowsAsOver() {
        addBudget("Fuel", 200, "INR", "Fuel");
        spend(260, "Fuel", EVERYDAY, inMonth(3));

        BudgetProgress progress = only();
        assertTrue(progress.isOver());
        assertEquals(-60, progress.left(), 0.001);
        assertEquals(100, progress.percent());
    }

    @Test
    public void editingReplacesTheCategoriesAndDeletingRemovesThem() {
        long id = addBudget("Eating", 1000, "INR", "Food");
        spend(100, "Food", EVERYDAY, inMonth(3));
        spend(40, "Movies", EVERYDAY, inMonth(3));

        Budget budget = only().budget;
        budget.setName("Fun");
        service.update(budget, Arrays.asList("Movies")).join();
        assertEquals("Fun", only().budget.getName());
        assertEquals(40, only().spent, 0.001);

        service.delete(budget).join();
        assertTrue(database.budgetDao().getAllSync().isEmpty());
        assertTrue(database.budgetDao().getAllCategoriesSync().isEmpty());
        assertEquals(id, budget.getId());
    }

    @Test
    public void newBudgetsGoToTheEndOfAnOrderedList() {
        long first = addBudget("B", 1, "INR", "Food");
        long second = addBudget("A", 1, "INR", "Food");
        // Alphabetical until someone reorders
        assertEquals("A", database.budgetDao().getAllSync().get(0).getName());

        service.reorder(Arrays.asList(first, second)).join();
        long third = addBudget("C", 1, "INR", "Food");
        List<Budget> all = database.budgetDao().getAllSync();
        assertEquals(first, all.get(0).getId());
        assertEquals(second, all.get(1).getId());
        assertEquals(third, all.get(2).getId());
    }
}
