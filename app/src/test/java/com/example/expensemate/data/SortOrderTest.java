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

    private void account(AppDatabase db, String name, String type) {
        Account account = new Account(name, "", "", null, "");
        account.setType(type);
        TestServices.over(db).accounts().add(account).join();
    }

    private static List<String> accountNames(AppDatabase db) {
        List<String> names = new ArrayList<>();
        for (Account account : db.accountDao().getAllAccountsSync()) {
            names.add(account.getName());
        }
        return names;
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
        account(database, "Trip", Account.TYPE_VIRTUAL);
        account(database, "Daily", Account.TYPE_VIRTUAL);
        account(database, "Wallet", Account.TYPE_REAL);
        account(database, "Bank", Account.TYPE_REAL);
        assertEquals(Arrays.asList("Daily", "Trip", "Bank", "Wallet"), accountNames(database));

        services().accounts().reorder(Arrays.asList(accountId(database, "Trip"), accountId(database, "Daily"))).join();
        // Virtual still comes first, and the real ones keep their own order
        assertEquals(Arrays.asList("Trip", "Daily", "Bank", "Wallet"), accountNames(database));

        account(database, "Gold", Account.TYPE_VIRTUAL);
        account(database, "Cash", Account.TYPE_REAL);
        assertEquals("A new account goes last in an ordered list, alphabetically in one that is not",
                Arrays.asList("Trip", "Daily", "Gold", "Bank", "Cash", "Wallet"), accountNames(database));
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
        account(database, "Daily", Account.TYPE_VIRTUAL);
        account(database, "Trip", Account.TYPE_VIRTUAL);
        services().accounts().reorder(Arrays.asList(accountId(database, "Trip"), accountId(database, "Daily"))).join();
        services().categories().add(new Category("Food", "EXPENSE")).join();
        services().categories().add(new Category("Fuel", "EXPENSE")).join();
        services().categories().reorder(Arrays.asList(categoryId(database, "Fuel"), categoryId(database, "Food")))
                .join();

        AppDatabase restored = newDatabase();
        try {
            for (String section : new String[] { "CATEGORIES", "ACCOUNTS" }) {
                load(restored, section, BackupDataLoader.entityText(section, database));
            }
            assertEquals(Arrays.asList("Trip", "Daily"), accountNames(restored));
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
