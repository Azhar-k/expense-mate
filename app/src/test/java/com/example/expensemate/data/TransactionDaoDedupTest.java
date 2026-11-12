package com.example.expensemate.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.example.expensemate.domain.CaptureService;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class TransactionDaoDedupTest {
    private static final long T0 = 1793447463000L;
    private static final long MINUTE = 60_000;
    private static final String BODY = "ICICI Bank Acct XX444 debited for Rs 600.00 on 31-Oct-26; TEST SHOP credited.";

    private AppDatabase database;
    private TransactionDao dao;
    private CaptureService capture;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        dao = database.transactionDao();
        capture = TestServices.over(database).capture();
    }

    @After
    public void tearDown() {
        database.close();
    }

    private static Transaction message(String body, String sender, long time, String source) {
        return keyed(new Transaction(600, body, new Date(time), "DEBIT", "TEST SHOP", body, sender), source, time);
    }

    private static Transaction truecaller(double amount, String merchant, long time) {
        String text = "•  SBI CARDS  •  " + merchant;
        Transaction transaction = new Transaction(amount, "RCS: " + merchant, new Date(time), "DEBIT", merchant, text,
                "SBI CARDS");
        transaction.setSource(TransactionKeys.SOURCE_TRUECALLER);
        transaction.setMessageTime(time);
        transaction.setMatchKey(TransactionKeys.matchKey(amount, "DEBIT", merchant));
        return transaction;
    }

    private static Transaction keyed(Transaction transaction, String source, long time) {
        transaction.setSource(source);
        transaction.setMessageTime(time);
        transaction.setDedupKey(TransactionKeys.bodyKey(transaction.getSmsBody()));
        transaction.setMatchKey(TransactionKeys.matchKey(transaction.getAmount(), transaction.getTransactionType(),
                transaction.getReceiverName()));
        return transaction;
    }

    private int rows() {
        return dao.getAllTransactionsSyncOrderByDateAsc().size();
    }

    @Test
    public void sameMessageAcrossMinuteBoundary_isOneRow() {
        long endOfMinute = T0 - T0 % MINUTE + 59_000;
        assertTrue(capture.recordIfNew(message(BODY, "AX-ICICIT-S", endOfMinute, TransactionKeys.SOURCE_SMS_BROADCAST)));
        assertFalse(capture.recordIfNew(message(BODY, "AX-ICICIT-S", endOfMinute + 3_000, TransactionKeys.SOURCE_NOTIFICATION)));
        assertEquals(1, rows());
    }

    @Test
    public void bankResendFromAnotherSenderId_isOneRow() {
        assertTrue(capture.recordIfNew(message(BODY, "JM-Pluxee-S", T0, TransactionKeys.SOURCE_SMS_BROADCAST)));
        assertFalse(capture.recordIfNew(message(BODY, "JD-Pluxee-S", T0 + 50_000, TransactionKeys.SOURCE_SMS_BROADCAST)));
        assertEquals(1, rows());
    }

    @Test
    public void identicalTextOutsideTheWindow_isANewRow() {
        assertTrue(capture.recordIfNew(message(BODY, "AX-ICICIT-S", T0, TransactionKeys.SOURCE_SMS_BROADCAST)));
        assertTrue(capture.recordIfNew(message(BODY, "AX-ICICIT-S", T0 + 16 * MINUTE, TransactionKeys.SOURCE_SMS_BROADCAST)));
        assertEquals(2, rows());
    }

    @Test
    public void scanMatchesRowWhoseDateWasEdited() {
        assertTrue(capture.recordIfNew(message(BODY, "AX-ICICIT-S", T0, TransactionKeys.SOURCE_SMS_BROADCAST)));
        Transaction saved = dao.getAllTransactionsSyncOrderByDateAsc().get(0);
        dao.updateTransaction(saved.getId(), saved.getAmount(), saved.getDescription(), new Date(T0 + 3 * 24 * 60 * MINUTE),
                saved.getTransactionType(), saved.getReceiverName(), saved.getSmsBody(), saved.getSmsSender(),
                saved.getCategory(), saved.getLinkedRecurringPaymentId(), saved.getAccountId(), saved.getRealAccountId(),
                saved.isExcludedFromSummary(), saved.isNeedsReview(), saved.getCurrency());

        assertFalse(capture.recordIfNew(message(BODY, "AX-ICICIT-S", T0 + 30_000, TransactionKeys.SOURCE_SMS_SCAN)));
        assertEquals(1, rows());
    }

    @Test
    public void truecallerAlertThenFullText_isOneRow() {
        String full = "Rs.55.00 spent on your SBI Credit Card ending with 2222 at CornerCafe on 09-10-26 via UPI (Ref No. 1).";
        assertTrue(capture.recordIfNew(truecaller(55, "Cornercafe", T0)));
        Transaction fullText = keyed(new Transaction(55, full, new Date(T0 + 307_000), "DEBIT", "CornerCafe", full,
                "sbi_cards_abc_agent@rbm.goog"), TransactionKeys.SOURCE_MMS_OBSERVER, T0 + 307_000);
        assertFalse(capture.recordIfNew(fullText));
        assertEquals(1, rows());
    }

    @Test
    public void fullTextThenTruecallerAlert_isOneRow() {
        String full = "Rs.55.00 spent on your SBI Credit Card ending with 2222 at CornerCafe on 09-10-26 via UPI (Ref No. 1).";
        assertTrue(capture.recordIfNew(keyed(new Transaction(55, full, new Date(T0), "DEBIT", "CornerCafe", full, "agent"),
                TransactionKeys.SOURCE_MMS_OBSERVER, T0)));
        assertFalse(capture.recordIfNew(truecaller(55, "Cornercafe", T0 + 60_000)));
        assertEquals(1, rows());
    }

    @Test
    public void repostedTruecallerAlert_isOneRow() {
        assertTrue(capture.recordIfNew(truecaller(55, "Shop", T0)));
        assertFalse(capture.recordIfNew(truecaller(55, "Shop", T0 + 45_000)));
        assertEquals(1, rows());
    }

    @Test
    public void truecallerAlertsForDifferentAmounts_areSeparateRows() {
        assertTrue(capture.recordIfNew(truecaller(100, "Bakery", T0)));
        assertTrue(capture.recordIfNew(truecaller(88, "Bakery", T0 + 30_000)));
        assertEquals(2, rows());
    }

    @Test
    public void differentFullTextMessagesWithSameAmountAndMerchant_areSeparateRows() {
        assertTrue(capture.recordIfNew(message(BODY + " Ref 1", "AX-ICICIT-S", T0, TransactionKeys.SOURCE_SMS_BROADCAST)));
        assertTrue(capture.recordIfNew(message(BODY + " Ref 2", "AX-ICICIT-S", T0 + MINUTE, TransactionKeys.SOURCE_SMS_BROADCAST)));
        assertEquals(2, rows());
    }

    @Test
    public void manualEntriesInTheSameMinute_areUnaffected() {
        Transaction first = new Transaction(100, "tea", new Date(T0), "DEBIT", "a", "", "");
        Transaction second = new Transaction(200, "lunch", new Date(T0), "DEBIT", "b", "", "");
        dao.insertTransaction(first);
        dao.insertTransaction(second);
        assertEquals(2, rows());
    }

    @Test
    public void concurrentEntryPaths_insertOneRow() throws Exception {
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Callable<Boolean> attempt = () -> {
                start.await();
                return capture.recordIfNew(message(BODY, "AX-ICICIT-S", T0, TransactionKeys.SOURCE_SMS_BROADCAST));
            };
            results.add(pool.submit(attempt));
        }
        start.countDown();

        int inserted = 0;
        for (Future<Boolean> result : results) {
            if (result.get()) {
                inserted++;
            }
        }
        pool.shutdown();
        assertEquals(1, inserted);
        assertEquals(1, rows());
    }
}
