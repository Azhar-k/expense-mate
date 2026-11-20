package com.example.expensemate.data;

import static org.junit.Assert.assertEquals;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.example.expensemate.domain.CategoryService;
import com.example.expensemate.domain.local.LocalServices;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SortOrderTest {
    private Context context;
    private AppDatabase database;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        database = newDatabase();
    }

    @After
    public void tearDown() {
        database.close();
    }

    private AppDatabase newDatabase() {
        return Room.inMemoryDatabaseBuilder(context, AppDatabase.class).allowMainThreadQueries().build();
    }

    private LocalServices services() {
        return TestServices.over(database);
    }

    private void account(AppDatabase db, String name) {
        TestServices.over(db).accounts().add(new Account(name, "", "", null, "")).join();
    }

    private void fund(AppDatabase db, String name) {
        TestServices.over(db).funds().add(new Fund(name, "")).join();
    }

    private static List<String> accountNames(AppDatabase db) {
        List<String> names = new ArrayList<>();
        for (Account account : db.accountDao().getAllAccountsSync()) {
            names.add(account.getName());
        }
        return names;
    }

    private static List<String> fundNames(AppDatabase db) {
        List<String> names = new ArrayList<>();
        for (Fund fund : db.fundDao().getAllFundsSync()) {
            names.add(fund.getName());
        }
        return names;
    }

    private static long fundId(AppDatabase db, String name) {
        for (Fund fund : db.fundDao().getAllFundsSync()) {
            if (fund.getName().equals(name)) {
                return fund.getId();
            }
        }
        throw new AssertionError(name);
    }

    private static List<String> categoryNames(AppDatabase db) {
        List<String> names = new ArrayList<>();
        for (Category category : db.categoryDao().getAllCategoriesSync()) {
            names.add(category.getName());
        }
        return names;
    }

    private static long accountId(AppDatabase db, String name) {
        for (Account account : db.accountDao().getAllAccountsSync()) {
            if (account.getName().equals(name)) {
                return account.getId();
            }
        }
        throw new AssertionError(name);
    }

    private static long categoryId(AppDatabase db, String name) {
        for (Category category : db.categoryDao().getAllCategoriesSync()) {
            if (category.getName().equals(name)) {
                return category.getId();
            }
        }
        throw new AssertionError(name);
    }

    @Test
    public void accountsStayAlphabeticalUntilReordered() {
        account(database, "Wallet");
        account(database, "Bank");
        assertEquals(Arrays.asList("Bank", "Wallet"), accountNames(database));

        services().accounts().reorder(Arrays.asList(accountId(database, "Wallet"), accountId(database, "Bank")))
                .join();
        assertEquals(Arrays.asList("Wallet", "Bank"), accountNames(database));

        account(database, "Cash");
        assertEquals("A new account goes last in an ordered list",
                Arrays.asList("Wallet", "Bank", "Cash"), accountNames(database));
    }

    @Test
    public void fundsStayAlphabeticalUntilReorderedApartFromAccounts() {
        fund(database, "Trip");
        fund(database, "Daily");
        account(database, "Bank");
        account(database, "Wallet");
        assertEquals(Arrays.asList("Daily", "Trip"), fundNames(database));

        services().funds().reorder(Arrays.asList(fundId(database, "Trip"), fundId(database, "Daily"))).join();
        assertEquals(Arrays.asList("Trip", "Daily"), fundNames(database));
        assertEquals("Accounts keep their own order", Arrays.asList("Bank", "Wallet"), accountNames(database));

        fund(database, "Gold");
        account(database, "Cash");
        assertEquals("A new fund goes last in an ordered list",
                Arrays.asList("Trip", "Daily", "Gold"), fundNames(database));
        assertEquals("A new account goes alphabetically in a list that is not ordered",
                Arrays.asList("Bank", "Cash", "Wallet"), accountNames(database));
    }

    @Test
    public void categoriesFollowTheSavedOrder() {
        CategoryService categories = services().categories();
        categories.add(new Category("Food", "EXPENSE")).join();
        categories.add(new Category("Fuel", "EXPENSE")).join();
        categories.add(new Category("Bills", "EXPENSE")).join();
        categories.reorder(Arrays.asList(categoryId(database, "Fuel"), categoryId(database, "Food"),
                categoryId(database, "Bills"))).join();
        categories.saveAsDefault(new Category("Rent", "EXPENSE")).join();

        assertEquals(Arrays.asList("Fuel", "Food", "Bills", "Rent"), categoryNames(database));
    }

    @Test
    public void theOrderSurvivesABackupAndAnOlderBackupStaysAlphabetical() throws Exception {
        fund(database, "Daily");
        fund(database, "Trip");
        services().funds().reorder(Arrays.asList(fundId(database, "Trip"), fundId(database, "Daily"))).join();
        account(database, "Bank");
        account(database, "Wallet");
        services().accounts().reorder(Arrays.asList(accountId(database, "Wallet"), accountId(database, "Bank")))
                .join();
        services().categories().add(new Category("Food", "EXPENSE")).join();
        services().categories().add(new Category("Fuel", "EXPENSE")).join();
        services().categories().reorder(Arrays.asList(categoryId(database, "Fuel"), categoryId(database, "Food")))
                .join();

        AppDatabase restored = newDatabase();
        try {
            for (String section : new String[] { "CATEGORIES", "FUNDS", "ACCOUNTS" }) {
                load(restored, section, BackupDataLoader.entityText(section, database));
            }
            assertEquals(Arrays.asList("Trip", "Daily"), fundNames(restored));
            assertEquals(Arrays.asList("Wallet", "Bank"), accountNames(restored));
            assertEquals(Arrays.asList("Fuel", "Food"), categoryNames(restored));
        } finally {
            restored.close();
        }

        AppDatabase old = newDatabase();
        try {
            load(old, "CATEGORIES", "ID: 1\nName: Fuel\nType: EXPENSE\n---\nID: 2\nName: Food\nType: EXPENSE\n---\n");
            assertEquals(Arrays.asList("Food", "Fuel"), categoryNames(old));
        } finally {
            old.close();
        }
    }

    private void load(AppDatabase target, String section, String text) throws Exception {
        File file = File.createTempFile("backup", ".txt", context.getCacheDir());
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(text);
        }
        new BackupDataLoader(context, target).loadSingleEntityFromFile(file, section);
        file.delete();
    }
}
