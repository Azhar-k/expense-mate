package com.example.expensemate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.app.Application;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.test.core.app.ApplicationProvider;

import com.example.expensemate.data.Account;
import com.example.expensemate.data.AppDatabase;
import com.example.expensemate.data.Category;
import com.example.expensemate.data.RecurringPayment;
import com.example.expensemate.data.Transaction;
import com.example.expensemate.data.Transfers;
import com.example.expensemate.ui.activity.TransactionDetailsSheet;
import com.example.expensemate.ui.activity.TransactionFormSheet;

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
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS,
                "android.permission.FOREGROUND_SERVICE_DATA_SYNC");
        app.getSharedPreferences("main_activity", Context.MODE_PRIVATE).edit()
                .putBoolean("notification_access_asked", true).apply();
        database = AppDatabase.getDatabase(app);
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

    private long salaryId;

    private void seed() throws Exception {
        Thread seeder = new Thread(() -> {
            database.clearAllTables();
            for (String name : new String[] { "Default", "Groceries", "Eating out", "Transport", "Home and rent", "Charity" }) {
                database.categoryDao().insertCategory(new Category(name, "EXPENSE"));
            }
            for (String name : new String[] { "Default", "Salary", "Business" }) {
                database.categoryDao().insertCategory(new Category(name, "INCOME"));
            }
            Account salary = new Account("Salary", "000011114821", "State Bank", null, "");
            salary.setDefault(true);
            database.accountDao().insert(salary);
            database.accountDao().insert(new Account("Household", "000022227730", "Axis Bank", null, ""));
            database.accountDao().insert(new Account("Savings", "000033331096", "HDFC Bank", null, ""));
            database.accountDao().insert(new Account("Credit card", "", "", daysAgo(-900, 9), ""));

            List<Account> accounts = database.accountDao().getAllAccountsSync();
            long credit = idOf(accounts, "Credit card");
            long household = idOf(accounts, "Household");
            long savings = idOf(accounts, "Savings");
            salaryId = idOf(accounts, "Salary");

            insert(1240, "DEBIT", "Groceries", household, daysAgo(0, 17), "Corner Grocery", false, null);
            insert(600, "DEBIT", "Default", salaryId, daysAgo(0, 11), "Ravi K.", true,
                    "Acct XX821 debited for Rs 600.00 to RAVI K");
            insert(85000, "CREDIT", "Salary", salaryId, daysAgo(1, 9), "Monthly salary", false, null);
            insert(320, "DEBIT", "Transport", household, daysAgo(1, 8), "City Metro", false, null);
            insert(450, "DEBIT", "Eating out", household, daysAgo(2, 20), "Noodle House", false, null);
            insert(24000, "DEBIT", "Home and rent", salaryId, daysAgo(3, 10), "Rent", false, null);
            insert(23000, "DEBIT", "Groceries", credit, daysAgo(4, 10), "Superstore", false, null);
            insert(2000, "DEBIT", "Charity", household, daysAgo(4, 12), "Food bank", false, null);

            Transaction[] transfer = Transfers.newPair(5000, household, savings, daysAgo(0, 15), "");
            database.transactionDao().insertTransfer(transfer[0], transfer[1]);
            Transaction[] start = Transfers.newPair(245000, salaryId, savings, daysAgo(5, 9), "Opening");
            database.transactionDao().insertTransfer(start[0], start[1]);
            insert(290000, "CREDIT", "Salary", salaryId, daysAgo(40, 9), "Opening balance", false, null);
            insert(25000, "CREDIT", "Business", household, daysAgo(40, 9), "Opening balance", false, null);

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
            savingsPlan.setFromAccountId(salaryId);
            savingsPlan.setToAccountId(savings);
            database.recurringPaymentDao().insert(savingsPlan);
        });
        seeder.start();
        seeder.join();
    }

    private void insert(double amount, String type, String category, long account, Date date, String receiver,
            boolean review, String message) {
        Transaction row = new Transaction();
        row.setAmount(amount);
        row.setTransactionType(type);
        row.setCategory(category);
        row.setAccountId(account);
        row.setDate(date);
        row.setReceiverName(receiver);
        row.setDescription("");
        row.setNeedsReview(review);
        row.setSmsBody(message);
        database.transactionDao().insertTransaction(row);
    }

    private static long idOf(List<Account> accounts, String name) {
        for (Account account : accounts) {
            if (account.getName().equals(name)) {
                return account.getId();
            }
        }
        throw new AssertionError(name);
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

    private String title() {
        return activity.getSupportActionBar().getTitle().toString();
    }

    private void assertShows(String text) {
        List<String> texts = visibleTexts();
        assertTrue("Expected \"" + text + "\" in " + texts, texts.contains(text));
    }

    private void runAllScreens() throws Exception {
        // Home: this month's money leaves transfers out
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
        assertShows("Household → Savings");
        assertShows("Today");
        shoot("activity");

        activity.openTab(R.id.navigation_bills);
        settle();
        assertTrue(title(), title().endsWith(" bills"));
        assertShows("Phone bill");
        assertShows("Monthly savings");
        assertShows("Ended");
        shoot("bills");

        activity.openTab(R.id.navigation_accounts);
        settle();
        assertEquals("Accounts", title());
        assertShows("State Bank ••4821");
        assertShows("Default");
        shoot("accounts");

        Bundle args = new Bundle();
        args.putLong("accountId", salaryId);
        nav.navigate(R.id.account_details_fragment, args);
        settle();
        assertEquals("Salary", title());
        assertEquals(View.GONE, activity.findViewById(R.id.nav_view).getVisibility());
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
                        && when.get(Calendar.MONTH) == now.get(Calendar.MONTH)
                        && when.get(Calendar.YEAR) == now.get(Calendar.YEAR)) {
                    total[0] += row.getAmount();
                }
            }
        });
        reader.start();
        reader.join();
        return com.example.expensemate.util.Money.format(total[0]).substring(1);
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

    private static void clickChip(Dialog dialog, String label) {
        android.view.ViewGroup chips = dialog.findViewById(R.id.chipsCategory);
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
        assertEquals("Choose a category", ((TextView) dialog.findViewById(R.id.tvCategoryLabel)).getText().toString());
        assertEquals(before, allRows().size());

        clickChip(dialog, "Eating out");
        dialog.findViewById(R.id.btnSave).performClick();
        settle();
        Transaction saved = findByReceiver("Test Cafe");
        assertNotNull(saved);
        assertEquals(250.50, saved.getAmount(), 0.001);
        assertEquals("Eating out", saved.getCategory());
        assertEquals("DEBIT", saved.getTransactionType());
        assertEquals(Long.valueOf(salaryId), saved.getAccountId());
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
    public void transferSheetSavesALinkedPairAndRefusesOneAccount() throws Exception {
        int before = allRows().size();
        Dialog dialog = openAddSheet();
        ((com.google.android.material.button.MaterialButtonToggleGroup) dialog.findViewById(R.id.typeToggle))
                .check(R.id.btnTransfer);
        settle();
        ((TextView) dialog.findViewById(R.id.etAmount)).setText("777");
        assertEquals("Move ₹777", ((TextView) dialog.findViewById(R.id.btnSave)).getText().toString());

        ((android.widget.AutoCompleteTextView) dialog.findViewById(R.id.etTo)).setText("Salary", false);
        dialog.findViewById(R.id.btnSave).performClick();
        settle();
        assertEquals("Same account", before, allRows().size());

        ((android.widget.AutoCompleteTextView) dialog.findViewById(R.id.etTo)).setText("Savings", false);
        dialog.findViewById(R.id.btnSave).performClick();
        settle();

        List<Transaction> rows = allRows();
        assertEquals(before + 2, rows.size());
        String group = null;
        int legs = 0;
        for (Transaction row : rows) {
            if (row.getAmount() == 777) {
                group = row.getTransferGroupId();
                assertNotNull(group);
                assertEquals(Transfers.CATEGORY, row.getCategory());
                legs++;
            }
        }
        assertEquals(2, legs);
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
