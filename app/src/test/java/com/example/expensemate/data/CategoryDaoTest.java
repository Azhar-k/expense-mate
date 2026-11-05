package com.example.expensemate.data;

import static org.junit.Assert.assertEquals;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.example.expensemate.domain.CategoryService;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class CategoryDaoTest {
    private AppDatabase database;
    private CategoryDao dao;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        dao = database.categoryDao();
    }

    @After
    public void tearDown() {
        database.close();
    }

    @Test
    public void sameNameIsKeptForBothTypes() {
        dao.insertIfMissing(new Category("Others", "EXPENSE"));
        dao.insertIfMissing(new Category("Others", "INCOME"));

        assertEquals(1, dao.countCategories("Others", "EXPENSE"));
        assertEquals(1, dao.countCategories("Others", "INCOME"));
    }

    @Test
    public void sameNameAndTypeIsNotAddedTwice() {
        dao.insertIfMissing(new Category("Others", "EXPENSE"));
        dao.insertIfMissing(new Category("Others", "EXPENSE"));

        assertEquals(1, dao.getAllCategoriesSync().size());
    }

    @Test
    public void onlyOneDefaultPerType() {
        CategoryService categories = TestServices.over(database).categories();
        categories.saveAsDefault(new Category("Default", "EXPENSE")).join();
        categories.saveAsDefault(new Category("Default", "INCOME")).join();
        categories.saveAsDefault(new Category("Food", "EXPENSE")).join();

        int expenseDefaults = 0;
        for (Category category : dao.getAllCategoriesSync()) {
            if (category.isDefault() && category.getType().equals("EXPENSE")) {
                expenseDefaults++;
                assertEquals("Food", category.getName());
            }
        }
        assertEquals(1, expenseDefaults);
        assertEquals("Default", dao.getDefaultName("INCOME"));
        assertEquals("Food", dao.getDefaultName("EXPENSE"));
    }
}
