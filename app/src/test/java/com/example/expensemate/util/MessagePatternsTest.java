package com.example.expensemate.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.expensemate.data.DefaultMessagePatterns;
import com.example.expensemate.data.MessagePattern;
import com.example.expensemate.data.Transaction;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** How user patterns read messages, and the drafts written from a sample message. */
public class MessagePatternsTest {

    private static Transaction read(MessagePattern pattern, String body) {
        return SmsTransactionHandler.readWith(pattern, body, "AX-TESTBK-S");
    }

    @Test
    public void namedGroupsGiveTheAmountAndMerchant() {
        MessagePattern pattern = new MessagePattern("UK", "DEBIT",
                "You paid £(?<amount>[\\d,.]+) to (?<merchant>.+?) on");
        Transaction t = read(pattern, "You paid £1,204.50 to TEST  CAFE on 3 Nov");
        assertEquals(1204.50, t.getAmount(), 0.001);
        assertEquals("TEST CAFE", t.getReceiverName());
        assertEquals("DEBIT", t.getTransactionType());
    }

    @Test
    public void unnamedGroupsAreReadByShape() {
        MessagePattern pattern = new MessagePattern("Plain", "CREDIT",
                "Card (XX\\d+) got ([\\d.]+) on (\\d{2}/\\d{2}) from (.+)\\.");
        Transaction t = read(pattern, "Card XX1234 got 75.25 on 03/11 from TEST FRIEND.");
        assertEquals(75.25, t.getAmount(), 0.001);
        assertEquals("TEST FRIEND", t.getReceiverName());
    }

    @Test
    public void theCounterpartyTemplateWrapsOrReplacesTheMerchant() {
        MessagePattern wrap = new MessagePattern("Wrap", "CREDIT", "NEFT (?<amount>\\d+) ref (?<merchant>\\w+)");
        wrap.setCounterparty("NEFT-{merchant}");
        assertEquals("NEFT-AB12", read(wrap, "NEFT 500 ref AB12").getReceiverName());

        MessagePattern fixed = new MessagePattern("Fixed", "CREDIT", "Salary (?<amount>\\d+)");
        fixed.setCounterparty("Employer");
        fixed.setExcludeFromSummary(true);
        Transaction t = read(fixed, "Salary 9000");
        assertEquals("Employer", t.getReceiverName());
        assertTrue(t.isExcludedFromSummary());
    }

    @Test
    public void anInvalidOrAmountlessPatternIsSkipped() {
        MessagePattern broken = new MessagePattern("Broken", "DEBIT", "Paid (?<amount>\\d+");
        MessagePattern noAmount = new MessagePattern("Words", "DEBIT", "Paid (?<amount>[a-z]+)");
        MessagePattern good = new MessagePattern("Good", "DEBIT", "Paid (?<amount>\\d+) at (?<merchant>\\w+)");
        Transaction t = SmsTransactionHandler.extractTransactionDetails("Paid 40 at SHOP",
                "X", Arrays.asList(broken, noAmount, good));
        assertEquals(40, t.getAmount(), 0.001);
        assertEquals("SHOP", t.getReceiverName());
    }

    @Test
    public void aPatternPutFirstWinsOverTheShippedOnes() {
        String body = "Rs 40.00 paid to TEST VENDOR";
        MessagePattern mine = new MessagePattern("Mine", "DEBIT", "Rs (?<amount>[\\d.]+) paid to (?<merchant>.+)");
        mine.setCounterparty("Mine: {merchant}");
        List<MessagePattern> patterns = new ArrayList<>();
        patterns.add(mine);
        patterns.addAll(DefaultMessagePatterns.all());
        assertEquals("Mine: TEST VENDOR",
                SmsTransactionHandler.extractTransactionDetails(body, "X", patterns).getReceiverName());
    }

    @Test
    public void noPatternsReadsNothing() {
        assertNull(SmsTransactionHandler.extractTransactionDetails("Rs 40.00 paid to TEST VENDOR", "X",
                new ArrayList<>()));
    }

    @Test
    public void everyShippedPatternHasAKeyAndCompiles() {
        java.util.Set<String> keys = new java.util.HashSet<>();
        for (MessagePattern pattern : DefaultMessagePatterns.all()) {
            assertNotNull(pattern.compiled());
            assertTrue("Duplicate key " + pattern.getBuiltinKey(), keys.add(pattern.getBuiltinKey()));
        }
    }

    // Duplicates and overlaps

    private static MessagePattern saved(long id, String name, String regex) {
        MessagePattern pattern = new MessagePattern(name, "DEBIT", regex);
        pattern.setId(id);
        return pattern;
    }

    private static final String CARD = "Card spend (?<amount>[\\d.]+) at (?<merchant>\\w+)";
    private static final String LOOSE = "spend (?<amount>[\\d.]+) at (?<merchant>\\w+)";
    private static final String SAMPLE = "Card spend 12.50 at SHOP";

    @Test
    public void theSameExpressionIsFoundButNotThePatternItself() {
        List<MessagePattern> list = Arrays.asList(saved(1, "Card", CARD), saved(2, "Loose", LOOSE));
        assertEquals("Card", PatternChecks.sameExpression(list, "  " + CARD + " ", 0).getName());
        assertNull(PatternChecks.sameExpression(list, CARD, 1)); // Editing Card without changing it
        assertNull(PatternChecks.sameExpression(list, "other (?<amount>\\d+)", 0));
    }

    @Test
    public void theSameNameIgnoresCase() {
        List<MessagePattern> list = Arrays.asList(saved(1, "My Bank", CARD));
        assertEquals("My Bank", PatternChecks.sameName(list, "my bank ", 0).getName());
        assertNull(PatternChecks.sameName(list, "My Bank", 1));
    }

    @Test
    public void aNewPatternIsToldWhichPatternAlreadyReadsTheMessage() {
        MessagePattern off = saved(1, "Off", CARD);
        off.setActive(false);
        List<MessagePattern> list = Arrays.asList(off, saved(2, "Loose", LOOSE), saved(3, "Card", CARD));
        PatternChecks.Overlap overlap = PatternChecks.firstOtherReader(list, SAMPLE, 0);
        assertEquals("Loose", overlap.pattern.getName()); // The first one on, in order
        assertEquals(12.50, overlap.reading.getAmount(), 0.001);
        assertEquals(false, overlap.winsOverThis); // New patterns go first
    }

    @Test
    public void anEditedPatternIsBeatenOnlyByOnesBeforeIt() {
        List<MessagePattern> list = Arrays.asList(saved(1, "Loose", LOOSE), saved(2, "Card", CARD),
                saved(3, "Last", LOOSE + "\\b"));
        assertTrue(PatternChecks.firstOtherReader(list, SAMPLE, 2).winsOverThis);
        assertEquals(false, PatternChecks.firstOtherReader(Arrays.asList(saved(2, "Card", CARD),
                saved(3, "Last", LOOSE)), SAMPLE, 2).winsOverThis);
        assertNull(PatternChecks.firstOtherReader(list, "nothing here", 2));
        assertNull(PatternChecks.firstOtherReader(list, "", 0));
    }

    // Drafts

    private static MessagePattern draft(String message, String amount, String merchant, String type) {
        int a = message.indexOf(amount);
        int m = merchant == null ? -1 : message.indexOf(merchant);
        String regex = PatternDraft.build(message, a, a + amount.length(), m, merchant == null ? -1 : m + merchant.length());
        assertNotNull(regex);
        return new MessagePattern("Draft", type, regex);
    }

    @Test
    public void aDraftReadsTheNextMessageFromTheSameBank() {
        MessagePattern pattern = draft("Rs 1,23,000.00 spent on ICICI Bank Card XX3333 on 03-Oct-26 at TEST HOSPITAL. "
                + "Avl Lmt: Rs 2,00,000.00.", "1,23,000.00", "TEST HOSPITAL", "DEBIT");
        Transaction t = read(pattern, "Rs 99.50 spent on ICICI Bank Card XX3333 on 21-Nov-26 at CORNER SHOP. "
                + "Avl Lmt: Rs 50.00.");
        assertNotNull("Draft did not match: " + pattern.getPattern(), t);
        assertEquals(99.50, t.getAmount(), 0.001);
        assertEquals("CORNER SHOP", t.getReceiverName());
    }

    @Test
    public void aDraftWithTheMerchantFirst() {
        MessagePattern pattern = draft("TESTMART charged your card $12.40 (balance $300.00)", "12.40", "TESTMART",
                "DEBIT");
        Transaction t = read(pattern, "OTHER STORE charged your card $1,005.00 (balance $3.00)");
        assertNotNull("Draft did not match: " + pattern.getPattern(), t);
        assertEquals(1005.00, t.getAmount(), 0.001);
        assertEquals("OTHER STORE", t.getReceiverName());
    }

    @Test
    public void aDraftWithOnlyAnAmountAndSpecialCharacters() {
        MessagePattern pattern = draft("[Bank] Salary (monthly) credited: EUR 2500.00 *ref 77", "2500.00", null,
                "CREDIT");
        Transaction t = read(pattern, "[Bank] Salary (monthly) credited: EUR 2600.10 *ref 78");
        assertNotNull("Draft did not match: " + pattern.getPattern(), t);
        assertEquals(2600.10, t.getAmount(), 0.001);
        assertNull(t.getReceiverName());
    }

    @Test
    public void aDraftWithTheMerchantAtTheEnd() {
        MessagePattern pattern = draft("Sent 50.00 to TEST FRIEND", "50.00", "TEST FRIEND", "DEBIT");
        assertEquals("ANOTHER PERSON", read(pattern, "Sent 7.00 to ANOTHER PERSON").getReceiverName());
    }

    @Test
    public void noDraftWithoutAnAmount() {
        assertNull(PatternDraft.build("Sent 50.00", -1, -1, -1, -1));
    }
}
