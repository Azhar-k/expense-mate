package com.example.expensemate.domain.local;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.expensemate.data.Account;
import com.example.expensemate.data.PastTransaction;
import com.example.expensemate.data.RecurringPayment;
import com.example.expensemate.data.Transaction;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SuggestionsTest {
    private static final Set<String> CHOOSABLE = new HashSet<>(Arrays.asList("Food", "Fuel", "Shopping",
            "Groceries"));

    private long nextId = 1;

    private PastTransaction reviewed(String payee, String type, String category) {
        PastTransaction row = new PastTransaction();
        row.id = nextId++;
        row.receiverName = payee;
        row.transactionType = type;
        row.category = category;
        return row;
    }

    private static Account real(long id, String number) {
        Account account = new Account("Bank " + id, number, "Bank", null, null);
        account.setId(id);
        account.setType(Account.TYPE_REAL);
        return account;
    }

    private static Date day(int dayOfMonth) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(2026, Calendar.NOVEMBER, dayOfMonth, 10, 0, 0);
        return calendar.getTime();
    }

    private static RecurringPayment bill(long id, String name, double amount, int dueDay) {
        RecurringPayment bill = new RecurringPayment(name, amount, dueDay, null);
        bill.setId(id);
        return bill;
    }

    private static Transaction spend(String payee, double amount, int dayOfMonth) {
        return new Transaction(amount, "", day(dayOfMonth), "DEBIT", payee, "", "AX-BANK-S");
    }

    @Test
    public void payeeNameDropsHandlesRailsAndNumbers() {
        assertEquals("rahul sharma", Suggestions.payeeName("rahul.sharma@okhdfc"));
        assertEquals("acme", Suggestions.payeeName("NEFT-ACME LTD"));
        assertEquals("swiggy instamart", Suggestions.payeeName("UPI/SWIGGY INSTAMART/4321"));
        assertNull(Suggestions.payeeName("paytmqr2810050501@paytm"));
        assertNull(Suggestions.payeeName(null));
        // Backups restore a missing name as this text
        assertNull(Suggestions.payeeName("null"));
    }

    @Test
    public void similarNamesAreTheSamePayeeButDifferentPeopleAreNot() {
        assertTrue(Suggestions.similarity("swiggy", "swiggy instamart") >= Suggestions.SAME_PAYEE);
        assertTrue(Suggestions.similarity("rahul sharma", "rahul verma") < Suggestions.SAME_PAYEE);
        assertEquals(1.0, Suggestions.similarity("amazon", "amazon"), 1e-9);
    }

    @Test
    public void realAccountComesFromTheMaskedNumberInTheMessage() {
        List<Account> accounts = Arrays.asList(real(1, "50100012341234"), real(2, "9876"));
        assertEquals(Long.valueOf(1), Suggestions.realAccountFromMessage(
                "ICICI Bank Acct XX1234 debited for Rs 500.00", accounts));
        assertEquals(Long.valueOf(2), Suggestions.realAccountFromMessage(
                "Spent Rs 200 on your card ending 9876 at AMAZON", accounts));
        assertNull(Suggestions.realAccountFromMessage("Rs 500 debited from XX5555", accounts));
    }

    @Test
    public void twoOfYourAccountsInOneMessageSuggestNeither() {
        List<Account> accounts = Arrays.asList(real(1, "1234"), real(2, "9876"));
        assertNull(Suggestions.realAccountFromMessage("Acct XX1234 debited, XX9876 credited", accounts));
    }

    @Test
    public void virtualAccountsAreNotMatchedByNumber() {
        Account budget = real(3, "1234");
        budget.setType(Account.TYPE_VIRTUAL);
        assertNull(Suggestions.realAccountFromMessage("Acct XX1234 debited", Collections.singletonList(budget)));
    }

    @Test
    public void realAccountFollowsTheBankSenderWhenPastMessagesAgree() {
        List<PastTransaction> history = new ArrayList<>();
        for (String sender : new String[] { "AX-ICICIT-S", "JD-ICICIT", "VM-ICICIT-T" }) {
            PastTransaction row = reviewed("X", "DEBIT", "Food");
            row.smsSender = sender;
            row.realAccountId = 7L;
            history.add(row);
        }
        assertEquals(Long.valueOf(7), Suggestions.realAccountFromSender("BZ-ICICIT-S", history));
        assertNull(Suggestions.realAccountFromSender("AX-KOTAKB-S", history));
    }

    @Test
    public void oneEarlierMessageIsNotEnoughToPickAnAccount() {
        PastTransaction row = reviewed("SWIGGY", "DEBIT", "Food");
        row.accountId = 4L;
        assertNull(Suggestions.accountFromPayee("SWIGGY", Collections.singletonList(row)));
    }

    @Test
    public void categoryComesFromSimilarPayees() {
        List<PastTransaction> history = Arrays.asList(
                reviewed("SWIGGY", "DEBIT", "Food"),
                reviewed("SWIGGY", "DEBIT", "Food"),
                reviewed("SHELL PETROL", "DEBIT", "Fuel"));
        assertEquals("Food", Suggestions.category("SWIGGY INSTAMART", "DEBIT", CHOOSABLE, history));
    }

    @Test
    public void unreviewedDefaultAndOtherTypeRowsDoNotVote() {
        PastTransaction waiting = reviewed("SWIGGY", "DEBIT", "Food");
        waiting.needsReview = true;
        List<PastTransaction> history = Arrays.asList(
                waiting,
                reviewed("SWIGGY", "DEBIT", "Default"),
                reviewed("SWIGGY", "CREDIT", "Refunds"));
        assertNull(Suggestions.category("SWIGGY", "DEBIT", CHOOSABLE, history));
    }

    @Test
    public void deletedCategoryIsNotSuggested() {
        List<PastTransaction> history = Arrays.asList(
                reviewed("SWIGGY", "DEBIT", "Eating out"),
                reviewed("SWIGGY", "DEBIT", "Eating out"));
        assertNull(Suggestions.category("SWIGGY", "DEBIT", CHOOSABLE, history));
    }

    @Test
    public void splitVotesSuggestNoCategory() {
        List<PastTransaction> history = Arrays.asList(
                reviewed("AMAZON", "DEBIT", "Shopping"),
                reviewed("AMAZON", "DEBIT", "Groceries"));
        assertNull(Suggestions.category("AMAZON", "DEBIT", CHOOSABLE, history));
    }

    @Test
    public void billWithTheSameAmountDueTodayIsSuggested() {
        List<RecurringPayment> bills = Arrays.asList(bill(1, "Rent", 15000, 1), bill(2, "Gym", 1500, 1));
        assertEquals(Long.valueOf(1), Suggestions.bill(spend("MR LANDLORD", 15000, 2), bills,
                Collections.emptyList()));
    }

    @Test
    public void billThePayeePaidBeforeIsSuggestedEvenWhenTheAmountMoves() {
        PastTransaction lastMonth = reviewed("BESCOM", "DEBIT", "Utilities");
        lastMonth.linkedRecurringPaymentId = 3L;
        List<RecurringPayment> bills = Collections.singletonList(bill(3, "Electricity", 1200, 10));
        assertEquals(Long.valueOf(3), Suggestions.bill(spend("BESCOM BANGALORE", 1500, 9), bills,
                Collections.singletonList(lastMonth)));
    }

    @Test
    public void billNamedLikeThePayeeIsSuggested() {
        List<RecurringPayment> bills = Collections.singletonList(bill(4, "Netflix", 649, 20));
        assertEquals(Long.valueOf(4), Suggestions.bill(spend("NETFLIX.COM", 649, 5), bills,
                Collections.emptyList()));
    }

    @Test
    public void paidTransferAndIncomeBillsAreNotSuggested() {
        RecurringPayment paid = bill(5, "Rent", 15000, 1);
        paid.setCompleted(true);
        paid.setLastCompletedDate(day(1));
        RecurringPayment transfer = bill(6, "Savings", 15000, 1);
        transfer.setFromAccountId(1L);
        transfer.setToAccountId(2L);
        List<RecurringPayment> bills = Arrays.asList(paid, transfer);
        assertNull(Suggestions.bill(spend("MR LANDLORD", 15000, 2), bills, Collections.emptyList()));

        Transaction income = spend("MR LANDLORD", 15000, 2);
        income.setTransactionType("CREDIT");
        assertNull(Suggestions.bill(income, Collections.singletonList(bill(7, "Rent", 15000, 1)),
                Collections.emptyList()));
    }

    @Test
    public void twoEquallyLikelyBillsSuggestNeither() {
        List<RecurringPayment> bills = Arrays.asList(bill(8, "Rent", 15000, 1), bill(9, "Loan EMI", 15000, 1));
        assertNull(Suggestions.bill(spend("SOMEONE", 15000, 1), bills, Collections.emptyList()));
    }
}
