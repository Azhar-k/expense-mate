package com.example.expensemate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.app.Application;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.test.core.app.ApplicationProvider;

import com.example.expensemate.data.Account;
import com.example.expensemate.data.AppDatabase;
import com.example.expensemate.data.Budget;
import com.example.expensemate.data.BudgetCategory;
import com.example.expensemate.data.Category;
import com.example.expensemate.data.Fund;
import com.example.expensemate.data.RecurringPayment;
import com.example.expensemate.data.Transaction;
import com.example.expensemate.data.Transfers;
import com.example.expensemate.ui.accounts.AccountDetailsFragment;
import com.example.expensemate.ui.activity.TransactionDetailsSheet;
import com.example.expensemate.ui.activity.TransactionFormSheet;
import com.example.expensemate.util.Money;
import com.google.android.material.tabs.TabLayout;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowDialog;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * Opens every screen with invented data, so a layout or wiring mistake fails here rather than on
 * a phone. Set EM_SHOTS_DIR to also save what each screen looks like.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ScreensTest {
    private AppDatabase database;
    private ActivityController<MainActivity> controller;
    private MainActivity activity;
    private NavController nav;

    @Before
    public void setUp() throws Exception {
        Application app = ApplicationProvider.getApplicationContext();
        // On a phone WorkManager starts itself; Settings watches its Back up now job
        androidx.work.testing.WorkManagerTestInitHelper.initializeTestWorkManager(app);
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS);
        app.getSharedPreferences("main_activity", Context.MODE_PRIVATE).edit()
                .putBoolean("notification_access_asked", true).apply();
        database = AppDatabase.getDatabase(app);
        database.getOpenHelper().getWritableDatabase(); // So the first-run defaults land before the seed
        settle();
        seed();
        settle();
        controller = Robolectric.buildActivity(MainActivity.class).setup();
        activity = controller.get();
        NavHostFragment host = (NavHostFragment) activity.getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        nav = host.getNavController();
        settle();
    }

    @After
    public void tearDown() throws Exception {
        controller.pause().stop().destroy();
        settle();
        database.close();
        Field instance = AppDatabase.class.getDeclaredField("INSTANCE");
        instance.setAccessible(true);
        instance.set(null, null);
        // The services hold the closed database, so the next test builds them again
        Field services = AppServices.class.getDeclaredField("INSTANCE");
        services.setAccessible(true);
        services.set(null, null);
    }

    private void settle() throws InterruptedException {
        for (int i = 0; i < 20; i++) {
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            Thread.sleep(25);
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    private static Date daysAgo(int days, int hour) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, -days);
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, 12);
        return calendar.getTime();
    }

    // A due day in this month, near today, so the seed reads the same on any date
    private static int dayOffset(int days) {
        Calendar calendar = Calendar.getInstance();
        int day = calendar.get(Calendar.DAY_OF_MONTH) + days;
        return Math.max(1, Math.min(day, calendar.getActualMaximum(Calendar.DAY_OF_MONTH)));
    }

    // Funds and accounts come from separate tables, so their ids can be equal
    private long everydayId;
    private long savingsFundId;
    private long salaryAccountId;

    private void seed() throws Exception {
        Thread seeder = new Thread(() -> {
            database.clearAllTables();
            for (String name : new String[] { "Default", "Groceries", "Eating out", "Transport", "Home and rent", "Charity" }) {
                database.categoryDao().insertCategory(new Category(name, "EXPENSE"));
            }
            for (String name : new String[] { "Default", "Salary", "Business" }) {
                database.categoryDao().insertCategory(new Category(name, "INCOME"));
            }
            // What money is for
            Fund everyday = new Fund("Everyday", "");
            everyday.setDefault(true);
            everydayId = database.fundDao().insert(everyday);
            Fund savingsFund = new Fund("Savings", "For a rainy day");
            savingsFund.setTarget(300000.0);
            savingsFundId = database.fundDao().insert(savingsFund);
            database.fundDao().insert(new Fund("Trip", "Goa in December"));

            // Where money is
            Account salary = new Account("Salary", "000011114821", "State Bank", null, "");
            salary.setCurrency("INR");
            salary.setDefault(true);
            salaryAccountId = database.accountDao().insert(salary);
            Account householdAccount = new Account("Household", "000022227730", "Axis Bank", null, "");
            householdAccount.setCurrency("INR");
            long household = database.accountDao().insert(householdAccount);
            Account card = new Account("Credit card", "", "", daysAgo(-900, 9), "");
            card.setCurrency("INR");
            long credit = database.accountDao().insert(card);

            insert(1240, "DEBIT", "Groceries", everydayId, household, daysAgo(0, 17), "Corner Grocery", false, null);
            insert(600, "DEBIT", "Default", everydayId, salaryAccountId, daysAgo(0, 11), "Ravi K.", true,
                    "Acct XX821 debited for Rs 600.00 to RAVI K");
            insert(85000, "CREDIT", "Salary", everydayId, salaryAccountId, daysAgo(1, 9), "Monthly salary", false, null);
            insert(320, "DEBIT", "Transport", everydayId, household, daysAgo(1, 8), "City Metro", false, null);
            insert(450, "DEBIT", "Eating out", everydayId, household, daysAgo(2, 20), "Noodle House", false, null);
            insert(24000, "DEBIT", "Home and rent", everydayId, salaryAccountId, daysAgo(3, 10), "Rent", false, null);
            insert(23000, "DEBIT", "Groceries", everydayId, credit, daysAgo(4, 10), "Superstore", false, null);
            insert(2000, "DEBIT", "Charity", everydayId, household, daysAgo(4, 12), "Food bank", false, null);

            // Setting money aside: it moves between funds and stays in the same account
            Transaction[] transfer = Transfers.newPair(5000, everydayId, savingsFundId, daysAgo(0, 15), "");
            transfer[0].setAccountId(household);
            transfer[1].setAccountId(household);
            database.transactionDao().insertTransaction(transfer[0]);
            database.transactionDao().insertTransaction(transfer[1]);
            Transaction[] start = Transfers.newPair(245000, everydayId, savingsFundId, daysAgo(5, 9), "Opening");
            start[0].setAccountId(salaryAccountId);
            start[1].setAccountId(salaryAccountId);
            database.transactionDao().insertTransaction(start[0]);
            database.transactionDao().insertTransaction(start[1]);
            insert(290000, "CREDIT", "Salary", everydayId, salaryAccountId, daysAgo(40, 9), "Opening balance", false, null);
            insert(25000, "CREDIT", "Business", everydayId, household, daysAgo(40, 9), "Opening balance", false, null);

            Date farOff = daysAgo(-300, 9);
            database.recurringPaymentDao().insert(new RecurringPayment("Phone bill", 799, dayOffset(-4), farOff));
            database.recurringPaymentDao().insert(new RecurringPayment("Electricity", 2200, dayOffset(3), farOff));
            database.recurringPaymentDao().insert(new RecurringPayment("Streaming plan", 499, dayOffset(8), farOff));
            RecurringPayment rent = new RecurringPayment("Rent", 24000, 1, farOff);
            rent.setCompleted(true);
            rent.setLastCompletedDate(new Date());
            database.recurringPaymentDao().insert(rent);
            database.recurringPaymentDao().insert(new RecurringPayment("Old gym plan", 900, 5, daysAgo(60, 9)));
            RecurringPayment savingsPlan = new RecurringPayment("Monthly savings", 10000, dayOffset(8), farOff);
            savingsPlan.setFromFundId(everydayId);
            savingsPlan.setToFundId(savingsFundId);
            database.recurringPaymentDao().insert(savingsPlan);

            Budget eating = new Budget();
            eating.setName("Eating");
            eating.setAmount(6000);
            eating.setCurrency("INR");
            long eatingId = database.budgetDao().insert(eating);
            database.budgetDao().insertCategory(new BudgetCategory(eatingId, "Groceries"));
            database.budgetDao().insertCategory(new BudgetCategory(eatingId, "Eating out"));
        });
        seeder.start();
        seeder.join();
    }

    private void insert(double amount, String type, String category, long fund, long account, Date date,
            String receiver, boolean review, String message) {
        Transaction row = new Transaction();
        row.setAmount(amount);
        row.setTransactionType(type);
        row.setCategory(category);
        row.setFundId(fund);
        row.setAccountId(account);
        row.setCurrency("INR");
        row.setDate(date);
        row.setReceiverName(receiver);
        row.setDescription("");
        row.setNeedsReview(review);
        row.setSmsBody(message);
        database.transactionDao().insertTransaction(row);
    }

    private void shoot(String name) throws Exception {
        settle();
        View root = activity.getWindow().getDecorView();
        layOut(root);
        save(root, name);
    }

    private void shootDialog(String name) throws Exception {
        settle();
        Dialog dialog = ShadowDialog.getLatestDialog();
        assertNotNull(name, dialog);
        View root = dialog.getWindow().getDecorView();
        layOut(root);
        save(root, name);
    }

    private void layOut(View root) {
        int width = activity.getResources().getDisplayMetrics().widthPixels;
        int height = activity.getResources().getDisplayMetrics().heightPixels;
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, width, height);
    }

    private void save(View root, String name) throws Exception {
        String dir = System.getenv("EM_SHOTS_DIR");
        if (dir == null || dir.isEmpty()) {
            return;
        }
        Bitmap bitmap = Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(bitmap));
        File folder = new File(dir);
        folder.mkdirs();
        String mode = activity.getResources().getBoolean(R.bool.light_system_bars) ? "light" : "dark";
        try (FileOutputStream out = new FileOutputStream(new File(folder, name + "_" + mode + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
    }

    private List<String> visibleTexts() {
        List<String> texts = new ArrayList<>();
        collect(activity.getWindow().getDecorView(), texts);
        return texts;
    }

    private static void collect(View view, List<String> texts) {
        if (view.getVisibility() != View.VISIBLE) {
            return;
        }
        if (view instanceof TextView) {
            texts.add(((TextView) view).getText().toString());
        }
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                collect(group.getChildAt(i), texts);
            }
        }
    }

    private void assertAccountChipChecked(String name) {
        android.view.ViewGroup chips = activity.findViewById(R.id.chipsAccount);
        for (int i = 0; i < chips.getChildCount(); i++) {
            com.google.android.material.chip.Chip chip = (com.google.android.material.chip.Chip) chips.getChildAt(i);
            assertEquals(chip.getText().toString(), name.contentEquals(chip.getText()), chip.isChecked());
        }
    }

    private String title() {
        return activity.getSupportActionBar().getTitle().toString();
    }

    private void assertShows(String text) {
        List<String> texts = visibleTexts();
        assertTrue("Expected \"" + text + "\" in " + texts, texts.contains(text));
    }

    private void runAllScreens() throws Exception {
        // Home opens on everyday money, the default fund, and this month's money leaves transfers out.
        // Its chips hold every fund and every account; only the default fund is chosen.
        assertAccountChipChecked("Everyday");
        assertShows("Spent ₹51,010".replace("51,010", spentThisMonth()));
        assertShows("Income ₹85,000");
        assertShows("1 transaction needs a category");
        assertShows("Where it went");
        assertShows("Phone bill");
        shoot("home");

        activity.openTab(R.id.navigation_activity);
        settle();
        assertEquals("Activity", title());
        assertShows("Corner Grocery");
        assertShows("Everyday → Savings");
        assertShows("Today");
        shoot("activity");

        activity.openTab(R.id.navigation_bills);
        settle();
        assertTrue(title(), title().endsWith(" bills"));
        assertShows("Phone bill");
        assertShows("Monthly savings");
        assertShows("Ended");
        shoot("bills");

        // Money opens on budgets; funds and accounts add up to the same total
        String savingsTarget = Money.format(50000, "INR") + " to go of " + Money.format(300000, "INR");
        activity.openTab(R.id.navigation_money);
        settle();
        assertEquals("Money", title());
        assertShows("Eating");
        assertShows("Eating out, Groceries");
        assertShows("Budget");
        assertFalse(visibleTexts().contains("Trip"));
        shoot("money_budgets");

        TabLayout tabs = activity.findViewById(R.id.tabs);
        tabs.selectTab(tabs.getTabAt(1));
        settle();
        assertShows("Net balance");
        assertShows("₹3,48,390");
        assertShows("Everyday");
        assertShows("Trip");
        assertShows(savingsTarget);
        assertShows("Fund");
        assertFalse(visibleTexts().contains("State Bank ••4821"));
        shoot("money_funds");

        tabs.selectTab(tabs.getTabAt(2));
        settle();
        assertShows("₹3,48,390");
        assertShows("State Bank ••4821");
        assertShows("Default");
        assertShows("Account");
        assertFalse(visibleTexts().contains(savingsTarget));
        shoot("money_accounts");
        tabs.selectTab(tabs.getTabAt(1));
        settle();

        // The same id can name a fund and an account, so details are opened by kind
        nav.navigate(R.id.account_details_fragment,
                AccountDetailsFragment.args(AccountDetailsFragment.KIND_FUND, savingsFundId));
        settle();
        assertEquals("Savings", title());
        assertEquals(View.GONE, activity.findViewById(R.id.nav_view).getVisibility());
        shoot("fund_details");
        nav.popBackStack();
        settle();

        nav.navigate(R.id.account_details_fragment,
                AccountDetailsFragment.args(AccountDetailsFragment.KIND_ACCOUNT, salaryAccountId));
        settle();
        assertEquals("Salary", title());
        assertShows("State Bank ••4821");
        shoot("account_details");

        nav.popBackStack();
        settle();
        activity.openTab(R.id.navigation_home);
        settle();
        nav.navigate(R.id.navigation_settings);
        settle();
        assertEquals("Settings", title());
        assertShows("SMS reading");
        assertShows("Not signed in");
        assertShows("Sign in with Google");
        shoot("settings");

        nav.navigate(R.id.navigation_categories);
        settle();
        assertEquals("Categories", title());
        assertShows("Groceries");
        shoot("categories");

        nav.popBackStack();
        settle();
        nav.navigate(R.id.navigation_sms_scan);
        settle();
        assertEquals("Scan messages", title());
        shoot("scan");

        nav.popBackStack();
        nav.popBackStack();
        settle();
        TransactionFormSheet.showAdd(activity);
        shootDialog("add_sheet");
        ((androidx.fragment.app.DialogFragment) activity.getSupportFragmentManager()
                .findFragmentByTag("transaction_form")).dismiss();
        settle();

        long detailsId = firstTransactionId();
        TransactionDetailsSheet.show(activity, detailsId);
        shootDialog("details_sheet");
        ((androidx.fragment.app.DialogFragment) activity.getSupportFragmentManager()
                .findFragmentByTag("transaction_details")).dismiss();
        settle();
    }

    // Expenses dated in this month; early in a month some of the seed falls in the previous one
    private String spentThisMonth() throws Exception {
        final double[] total = { 0 };
        Calendar now = Calendar.getInstance();
        Thread reader = new Thread(() -> {
            for (Transaction row : database.transactionDao().getAllTransactionsSyncOrderByDateAsc()) {
                Calendar when = Calendar.getInstance();
                when.setTime(row.getDate());
                if ("DEBIT".equals(row.getTransactionType()) && row.getTransferGroupId() == null
                        && Long.valueOf(everydayId).equals(row.getFundId())
                        && when.get(Calendar.MONTH) == now.get(Calendar.MONTH)
                        && when.get(Calendar.YEAR) == now.get(Calendar.YEAR)) {
                    total[0] += row.getAmount();
                }
            }
        });
        reader.start();
        reader.join();
        return com.example.expensemate.util.Money.format(total[0], "INR").substring(1);
    }

    private long firstTransactionId() throws Exception {
        final long[] id = { 0 };
        Thread reader = new Thread(() -> {
            for (Transaction row : database.transactionDao().getAllTransactionsSyncOrderByDateAsc()) {
                if ("Ravi K.".equals(row.getReceiverName())) {
                    id[0] = row.getId();
                }
            }
        });
        reader.start();
        reader.join();
        return id[0];
    }

    private Dialog openMoveSheet(Long fromFundId, Long fromAccountId) throws Exception {
        TransactionFormSheet.showMoveOrTransfer(activity, fromFundId, fromAccountId);
        settle();
        return ShadowDialog.getLatestDialog();
    }

    private Dialog openAddSheet() throws Exception {
        TransactionFormSheet.showAdd(activity);
        settle();
        return ShadowDialog.getLatestDialog();
    }

    private List<Transaction> allRows() throws Exception {
        final List<Transaction> rows = new ArrayList<>();
        Thread reader = new Thread(() -> rows.addAll(database.transactionDao().getAllTransactionsSyncOrderByDateAsc()));
        reader.start();
        reader.join();
        return rows;
    }

    private Transaction findByReceiver(String receiver) throws Exception {
        for (Transaction row : allRows()) {
            if (receiver.equals(row.getReceiverName())) {
                return row;
            }
        }
        return null;
    }

    /** Taps a category chip: the details sheet's own row, or the add form's category picker. */
    private static void clickChip(Dialog dialog, String label) {
        android.view.ViewGroup chips = dialog.findViewById(R.id.chipsCategory);
        if (chips == null) {
            chips = dialog.findViewById(R.id.pickCategory).findViewById(R.id.chips);
        }
        clickChip(chips, label);
    }

    /** Taps a chip in one of the add form's pickers, such as the "to" account. */
    private static void clickChip(Dialog dialog, int picker, String label) {
        clickChip((android.view.ViewGroup) dialog.findViewById(picker).findViewById(R.id.chips), label);
    }

    private static void clickChip(android.view.ViewGroup chips, String label) {
        for (int i = 0; i < chips.getChildCount(); i++) {
            TextView chip = (TextView) chips.getChildAt(i);
            if (label.contentEquals(chip.getText())) {
                chip.performClick();
                return;
            }
        }
        throw new AssertionError("No chip " + label);
    }

    @Test
    public void addSheetNeedsAnAmountAndACategoryThenSaves() throws Exception {
        int before = allRows().size();
        Dialog dialog = openAddSheet();
        dialog.findViewById(R.id.btnSave).performClick();
        settle();
        assertEquals(View.VISIBLE, dialog.findViewById(R.id.tvAmountError).getVisibility());
        assertEquals(before, allRows().size());

        ((TextView) dialog.findViewById(R.id.etAmount)).setText("250.50");
        ((TextView) dialog.findViewById(R.id.etReceiver)).setText("Test Cafe");
        dialog.findViewById(R.id.btnSave).performClick();
        settle();
        assertEquals("Choose a category", ((TextView) dialog.findViewById(R.id.pickCategory).findViewById(R.id.tvLabel)).getText().toString());
        assertEquals(before, allRows().size());

        clickChip(dialog, "Eating out");
        dialog.findViewById(R.id.btnSave).performClick();
        settle();
        Transaction saved = findByReceiver("Test Cafe");
        assertNotNull(saved);
        assertEquals(250.50, saved.getAmount(), 0.001);
        assertEquals("Eating out", saved.getCategory());
        assertEquals("DEBIT", saved.getTransactionType());
        assertEquals("Goes to the everyday fund", Long.valueOf(everydayId), saved.getFundId());
        assertEquals("Goes to the default account", Long.valueOf(salaryAccountId), saved.getAccountId());
    }

    @Test
    public void incomeOffersIncomeCategories() throws Exception {
        Dialog dialog = openAddSheet();
        ((com.google.android.material.button.MaterialButtonToggleGroup) dialog.findViewById(R.id.typeToggle))
                .check(R.id.btnIncome);
        settle();
        clickChip(dialog, "Business");
    }

    @Test
    public void addSheetOffersOnlyExpenseAndIncome() throws Exception {
        Dialog dialog = openAddSheet();
        assertEquals(View.VISIBLE, dialog.findViewById(R.id.btnExpense).getVisibility());
        assertEquals(View.VISIBLE, dialog.findViewById(R.id.btnIncome).getVisibility());
        assertEquals(View.GONE, dialog.findViewById(R.id.btnMove).getVisibility());
        assertEquals(View.GONE, dialog.findViewById(R.id.btnTransfer).getVisibility());
    }

    @Test
    public void moveSheetChangesOnlyTheFund() throws Exception {
        int before = allRows().size();
        // Its own sheet, which starts as a move until a transfer has been made
        Dialog dialog = openMoveSheet(null, null);
        assertEquals(View.GONE, dialog.findViewById(R.id.btnExpense).getVisibility());
        assertEquals(View.VISIBLE, dialog.findViewById(R.id.btnTransfer).getVisibility());
        ((TextView) dialog.findViewById(R.id.etAmount)).setText("777");
        assertEquals("Move ₹777", ((TextView) dialog.findViewById(R.id.btnSave)).getText().toString());
        // Every account holds rupees, so there is no account to pick: a move never leaves one
        assertEquals(View.VISIBLE, dialog.findViewById(R.id.pickFromFund).getVisibility());
        assertEquals(View.GONE, dialog.findViewById(R.id.pickAccount).getVisibility());
        assertEquals(View.GONE, dialog.findViewById(R.id.pickFromAccount).getVisibility());

        clickChip(dialog, R.id.pickToFund, "Everyday");
        dialog.findViewById(R.id.btnSave).performClick();
        settle();
        assertEquals("A move to the same fund moves nothing", before, allRows().size());

        clickChip(dialog, R.id.pickToFund, "Savings");
        dialog.findViewById(R.id.btnSave).performClick();
        settle();

        List<Transaction> legs = rowsWithAmount(777);
        assertEquals(before + 2, allRows().size());
        assertEquals(2, legs.size());
        assertNotNull(legs.get(0).getTransferGroupId());
        assertEquals(legs.get(0).getTransferGroupId(), legs.get(1).getTransferGroupId());
        assertEquals(Transfers.CATEGORY, legs.get(0).getCategory());
        assertEquals(legs.get(0).getAccountId(), legs.get(1).getAccountId());
        assertTrue(Transfers.isMove(legs.get(0), legs.get(1)));
    }

    @Test
    public void transferSheetChangesOnlyTheAccount() throws Exception {
        int before = allRows().size();
        // Opened from an account's page, so a transfer out of it
        Dialog dialog = openMoveSheet(null, salaryAccountId);
        ((TextView) dialog.findViewById(R.id.etAmount)).setText("888");
        assertEquals("Transfer ₹888", ((TextView) dialog.findViewById(R.id.btnSave)).getText().toString());
        // The money keeps its purpose, so no fund is asked for
        assertEquals(View.VISIBLE, dialog.findViewById(R.id.pickToAccount).getVisibility());
        assertEquals(View.GONE, dialog.findViewById(R.id.pickFromFund).getVisibility());
        assertEquals(View.GONE, dialog.findViewById(R.id.pickToFund).getVisibility());

        dialog.findViewById(R.id.btnSave).performClick();
        settle();
        assertEquals("Nowhere to go picked yet", before, allRows().size());

        clickChip(dialog, R.id.pickToAccount, "Household");
        dialog.findViewById(R.id.btnSave).performClick();
        settle();

        List<Transaction> legs = rowsWithAmount(888);
        assertEquals(2, legs.size());
        assertEquals(legs.get(0).getTransferGroupId(), legs.get(1).getTransferGroupId());
        assertEquals(legs.get(0).getFundId(), legs.get(1).getFundId());
        assertEquals(Long.valueOf(everydayId), legs.get(0).getFundId());
        assertTrue(!legs.get(0).getAccountId().equals(legs.get(1).getAccountId()));
        assertTrue(!Transfers.isMove(legs.get(0), legs.get(1)));
    }

    private List<Transaction> rowsWithAmount(double amount) throws Exception {
        List<Transaction> found = new java.util.ArrayList<>();
        for (Transaction row : allRows()) {
            if (row.getAmount() == amount) {
                found.add(row);
            }
        }
        return found;
    }

    @Test
    public void detailsSheetSetsACategoryAndKeepsExclusionThroughDelete() throws Exception {
        Transaction row = findByReceiver("Ravi K.");
        assertTrue(row.isNeedsReview());
        TransactionDetailsSheet.show(activity, row.getId());
        settle();
        Dialog dialog = ShadowDialog.getLatestDialog();

        clickChip(dialog, "Charity");
        settle();
        Transaction updated = findByReceiver("Ravi K.");
        assertEquals("Charity", updated.getCategory());
        assertTrue(!updated.isNeedsReview());

        dialog.findViewById(R.id.switchCount).performClick();
        settle();
        assertTrue(findByReceiver("Ravi K.").isExcludedFromSummary());

        int before = allRows().size();
        dialog.findViewById(R.id.btnDelete).performClick();
        settle();
        assertEquals(before - 1, allRows().size());
    }

    @Test
    public void everyScreenOpensInLightMode() throws Exception {
        runAllScreens();
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-night-xxhdpi")
    public void everyScreenOpensInDarkMode() throws Exception {
        runAllScreens();
    }
}
