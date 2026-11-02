package com.example.expensemate.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Date;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class LoanDaoTest {
    private AppDatabase database;
    private LoanDao loans;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).allowMainThreadQueries().build();
        loans = database.loanDao();
    }

    @After
    public void tearDown() {
        database.close();
    }

    private long transaction(long id, String type, double amount, long time) {
        Transaction row = new Transaction();
        row.setId(id);
        row.setTransactionType(type);
        row.setAmount(amount);
        row.setDate(new Date(time));
        database.transactionDao().insertTransaction(row);
        return id;
    }

    private Loan loan(double amount) {
        Loan loan = new Loan();
        loan.setPerson("Ravi");
        loan.setAmount(amount);
        loan.setDate(new Date(1000));
        return loan;
    }

    private LoanSummary only() {
        assertEquals(1, loans.getLoanSummariesSync().size());
        return loans.getLoanSummariesSync().get(0);
    }

    @Test
    public void returnsAddUpUntilTheLoanIsBack() {
        long lent = transaction(1, "DEBIT", 5000, 1000);
        long id = loans.insertFromTransaction(loan(5000), lent);

        // The payment that lent the money is linked, but is not a return
        assertEquals(0, only().returned, 0);
        assertFalse(only().isCompleted());

        loans.setLoan(transaction(2, "CREDIT", 2000, 2000), id);
        assertEquals(2000, only().returned, 0);
        assertEquals(3000, only().outstanding(), 0);
        assertFalse(only().isCompleted());

        loans.setLoan(transaction(3, "CREDIT", 3000, 3000), id);
        assertTrue(only().isCompleted());
        assertEquals(0, only().outstanding(), 0);
        assertEquals(3000, only().completedDate().getTime());

        // Unlinking a return opens it again
        loans.setLoan(3, null);
        assertFalse(only().isCompleted());
        assertEquals(3000, only().outstanding(), 0);
    }

    @Test
    public void returnsRecordedByHandCount() {
        Loan loan = loan(1000);
        loan.setId(loans.insert(loan));
        loan.setReturnedManually(400);
        loans.update(loan);
        loans.setLoan(transaction(1, "CREDIT", 600, 2000), loan.getId());

        assertEquals(1000, only().returned, 0);
        assertTrue(only().isFullyReturned());
        assertTrue(only().isCompleted());
        assertEquals(2000, only().completedDate().getTime());

        // The last of it came back by hand after the linked return
        loan.setClosedDate(new Date(9000));
        loans.update(loan);
        assertEquals(9000, only().completedDate().getTime());
    }

    @Test
    public void markingReturnedByHandCompletesWhatIsLeft() {
        Loan loan = loan(1000);
        loan.setId(loans.insert(loan));
        loans.setLoan(transaction(1, "CREDIT", 300, 2000), loan.getId());
        loan.setClosedManually(true);
        loan.setClosedDate(new Date(5000));
        loans.update(loan);

        assertTrue(only().isCompleted());
        assertFalse(only().isFullyReturned());
        assertEquals(0, only().outstanding(), 0);
        assertEquals(5000, only().completedDate().getTime());
    }

    @Test
    public void deletingALoanKeepsItsTransactions() {
        long lent = transaction(1, "DEBIT", 500, 1000);
        long id = loans.insertFromTransaction(loan(500), lent);
        loans.setLoan(transaction(2, "CREDIT", 200, 2000), id);

        loans.delete(loans.getAllLoansSync().get(0));

        assertTrue(loans.getAllLoansSync().isEmpty());
        assertEquals(2, database.transactionDao().getAllTransactionsSyncOrderByDateAsc().size());
        for (Transaction row : database.transactionDao().getAllTransactionsSyncOrderByDateAsc()) {
            assertNull(row.getLoanId());
        }
    }

    @Test
    public void editingATransactionKeepsItsLoan() {
        long id = loans.insertFromTransaction(loan(500), transaction(1, "DEBIT", 500, 1000));
        Transaction row = database.transactionDao().getAllTransactionsSyncOrderByDateAsc().get(0);
        row.setDescription("edited");
        database.transactionDao().updateRow(row);

        assertEquals(Long.valueOf(id), database.transactionDao().getAllTransactionsSyncOrderByDateAsc().get(0).getLoanId());
    }
}
