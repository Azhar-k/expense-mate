package com.example.expensemate.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.example.expensemate.domain.local.LocalServices;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Date;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class CounterpartyCategoryTest {
    private static final long T0 = 1793447463000L;
    private static final long HOUR = 60L * 60 * 1000;

    private AppDatabase database;
    private TransactionDao dao;
    private CategoryDao categories;
    private LocalServices services;
    private long nextTime = T0;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        dao = database.transactionDao();
        categories = database.categoryDao();
        services = TestServices.over(database);
        services.categories().saveAsDefault(new Category("Default", "EXPENSE")).join();
        categories.insertCategory(new Category("Food", "EXPENSE"));
        categories.insertCategory(new Category("Fuel", "EXPENSE"));
        categories.insertCategory(new Category("Food", "INCOME"));
        categories.insertCategory(new Category("Salary", "INCOME"));
    }

    @After
    public void tearDown() {
        database.close();
    }

    /** A captured message, each one far enough apart not to count as a duplicate. */
    private Transaction captured(String counterparty, String type) {
        nextTime += HOUR;
        String body = type + " message " + nextTime;
        Transaction row = new Transaction(100, body, new Date(nextTime), type, counterparty, body, "AX-BANK-S");
        row.setSource(TransactionKeys.SOURCE_SMS_BROADCAST);
        row.setMessageTime(nextTime);
        row.setDedupKey(TransactionKeys.bodyKey(body));
        row.setNeedsReview(true);
        return row;
    }

    private Transaction chosen(String counterparty, String type, String category) {
        Transaction row = captured(counterparty, type);
        row.setCategory(category);
        row.setNeedsReview(false);
        return row;
    }

    private Transaction capture(String counterparty, String type) {
        Transaction row = captured(counterparty, type);
        assertTrue(services.capture().recordIfNew(row));
        return row;
    }

    private static void assertWaitingForCategory(Transaction row) {
        assertEquals("Default", row.getCategory());
        assertTrue(row.isNeedsReview());
    }

    @Test
    public void unknownCounterpartyWaitsForACategory() {
        assertWaitingForCategory(capture("SWIGGY", "DEBIT"));
    }

    @Test
    public void capturedPaymentGetsTheCategoryMarkedDefault() {
        defaults(find("Fuel", "EXPENSE"));
        defaults(find("Salary", "INCOME"));

        Transaction debit = capture("SWIGGY", "DEBIT");
        assertEquals("Fuel", debit.getCategory());
        assertTrue(debit.isNeedsReview());
        assertEquals("Salary", capture("ACME", "CREDIT").getCategory());
    }

    @Test
    public void rememberedCategoryBeatsTheDefault() {
        defaults(find("Fuel", "EXPENSE"));
        add(chosen("SWIGGY", "DEBIT", "Food"));

        assertEquals("Food", capture("SWIGGY", "DEBIT").getCategory());
    }

    @Test
    public void choosingTheMarkedDefaultIsNotRemembered() {
        defaults(find("Fuel", "EXPENSE"));
        add(chosen("SWIGGY", "DEBIT", "Fuel"));

        assertTrue(capture("SWIGGY", "DEBIT").isNeedsReview());
    }

    @Test
    public void capturedPaymentGetsTheChosenCategoryAndSkipsReview() {
        add(chosen("SWIGGY", "DEBIT", "Food"));

        Transaction next = capture("SWIGGY", "DEBIT");

        assertEquals("Food", next.getCategory());
        assertFalse(next.isNeedsReview());
    }

    @Test
    public void spacingCaseAndPunctuationAreTheSameCounterparty() {
        add(chosen("Swiggy Ltd.", "DEBIT", "Food"));

        assertEquals("Food", capture("SWIGGY  LTD", "DEBIT").getCategory());
    }

    @Test
    public void paymentsInAndOutAreMappedSeparately() {
        add(chosen("ACME", "DEBIT", "Food"));
        assertWaitingForCategory(capture("ACME", "CREDIT"));

        add(chosen("ACME", "CREDIT", "Salary"));
        assertEquals("Salary", capture("ACME", "CREDIT").getCategory());
        assertEquals("Food", capture("ACME", "DEBIT").getCategory());
    }

    @Test
    public void choosingOnADetailsScreenUpdatesTheMapping() {
        Transaction first = captured("SWIGGY", "DEBIT");
        first.setId(1);
        dao.insertTransaction(first);

        services.transactions().chooseCategory(first.getId(), "Food").join();
        assertEquals("Food", capture("SWIGGY", "DEBIT").getCategory());

        services.transactions().chooseCategory(first.getId(), "Fuel").join();
        assertEquals("Fuel", capture("SWIGGY", "DEBIT").getCategory());
    }

    @Test
    public void choosingDefaultForgetsTheMapping() {
        Transaction first = chosen("SWIGGY", "DEBIT", "Food");
        first.setId(1);
        add(first);

        services.transactions().chooseCategory(first.getId(), "Default").join();

        assertWaitingForCategory(capture("SWIGGY", "DEBIT"));
    }

    @Test
    public void editingAnOldRowWithoutChangingTheChoiceKeepsTheNewerMapping() {
        Transaction old = chosen("SWIGGY", "DEBIT", "Food");
        old.setId(1);
        add(old);
        add(chosen("SWIGGY", "DEBIT", "Fuel"));

        Transaction edited = old.copy();
        edited.setDescription("a new note");
        services.transactions().update(edited).join();
        assertEquals("Fuel", capture("SWIGGY", "DEBIT").getCategory());

        edited = old.copy();
        edited.setCategory("Food");
        edited.setReceiverName("ZOMATO");
        services.transactions().update(edited).join();
        assertEquals("Food", capture("ZOMATO", "DEBIT").getCategory());
    }

    @Test
    public void reviewedRowFromTheFormCountsAsAChoice() {
        Transaction waiting = captured("SWIGGY", "DEBIT");
        waiting.setId(1);
        dao.insertTransaction(waiting);

        Transaction reviewed = waiting.copy();
        reviewed.setCategory("Food");
        reviewed.setNeedsReview(false);
        services.transactions().update(reviewed).join();

        assertEquals("Food", capture("SWIGGY", "DEBIT").getCategory());
    }

    @Test
    public void renamedCategoryKeepsTheMapping() {
        add(chosen("SWIGGY", "DEBIT", "Food"));

        Category food = find("Food", "EXPENSE");
        food.setName("Eating out");
        categories.updateCategory(food);

        assertEquals("Eating out", capture("SWIGGY", "DEBIT").getCategory());
    }

    @Test
    public void deletedCategoryIsNotAssigned() {
        add(chosen("SWIGGY", "DEBIT", "Food"));

        categories.deleteCategory(find("Food", "EXPENSE"));

        assertWaitingForCategory(capture("SWIGGY", "DEBIT"));
    }

    @Test
    public void transfersAndBlankNamesAreNotMapped() {
        Transaction[] legs = Transfers.newPair(100, 1, 2, new Date(T0), "");
        legs[0].setReceiverName("MOVED");
        legs[0].setCategory("Food");
        add(legs[0]);
        add(chosen("", "DEBIT", "Food"));
        add(chosen("null", "DEBIT", "Food"));

        assertWaitingForCategory(capture("MOVED", "DEBIT"));
        assertWaitingForCategory(capture("", "DEBIT"));
        assertWaitingForCategory(capture("null", "DEBIT"));
    }

    @Test
    public void duplicateIsStillRejected() {
        add(chosen("SWIGGY", "DEBIT", "Food"));
        Transaction row = capture("SWIGGY", "DEBIT");

        Transaction again = captured("SWIGGY", "DEBIT");
        again.setDedupKey(row.getDedupKey());
        again.setMessageTime(row.getMessageTime());

        assertFalse(services.capture().recordIfNew(again));
    }

    @Test
    public void rebuildUsesTheNewestChosenCategory() {
        dao.insertTransaction(chosen("SWIGGY", "DEBIT", "Fuel"));
        dao.insertTransaction(chosen("SWIGGY", "DEBIT", "Food"));
        dao.insertTransaction(captured("WAITING", "DEBIT"));
        Transaction[] legs = Transfers.newPair(100, 1, 2, new Date(T0), "");
        legs[0].setReceiverName("MOVED");
        legs[0].setCategory("Food");
        dao.insertTransaction(legs[0]);

        CounterpartyCategory.rebuildFromTransactions(database.getOpenHelper().getWritableDatabase());

        assertEquals("Food", capture("SWIGGY", "DEBIT").getCategory());
        assertWaitingForCategory(capture("WAITING", "DEBIT"));
        assertWaitingForCategory(capture("MOVED", "DEBIT"));
    }

    @Test
    public void counterpartyKeyIsNullWithoutAName() {
        assertNull(TransactionKeys.counterpartyKey(null));
        assertNull(TransactionKeys.counterpartyKey("  "));
        assertNull(TransactionKeys.counterpartyKey("null"));
        assertNull(TransactionKeys.counterpartyKey("--"));
    }

    private void defaults(Category category) {
        services.categories().saveAsDefault(category).join();
    }

    /** Saved from the form, as a person's own choice. */
    private void add(Transaction row) {
        services.transactions().add(row).join();
    }

    private Category find(String name, String type) {
        List<Category> all = categories.getAllCategoriesSync();
        for (Category category : all) {
            if (category.getName().equals(name) && category.getType().equals(type)) {
                return category;
            }
        }
        throw new AssertionError("No category " + name);
    }
}
