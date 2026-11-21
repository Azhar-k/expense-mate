package com.example.expensemate.data;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;

import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.expensemate.service.GoogleDriveService;
import com.example.expensemate.service.BackupScheduler;
import com.example.expensemate.util.Currencies;
import com.example.expensemate.util.Periods;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Backs up to Google Drive as one text file per table and restores from them.
 *
 * Each record is a block of "Key: value" lines ending with "---". Files from format 2 on start
 * with a "Format: n" record (this version writes {@value #FORMAT_LINE}): their values are escaped,
 * so text can hold line breaks, null is written as \N, and dates are milliseconds. Files without
 * that line come from older versions and are read the way those versions wrote them.
 *
 * Format 3 keeps funds in their own file. Format 2 and older kept them in the accounts file, as rows
 * whose Account Type was not REAL, and named a transaction's fund "Account id" and its account
 * "Real account id", and a bill's funds "From Account ID" and "To Account ID".
 */
public class BackupDataLoader {
    private static final String TAG = "BackupDataLoader";
    static final int FORMAT = 3;
    static final String FORMAT_LINE = "Format: " + FORMAT;
    private static final String FORMAT_PREFIX = "Format: ";
    private static final String NULL = "\\N";

    /** Restore order: transactions refer to funds, accounts, bills and loans, mappings to categories. */
    static final String[][] FILES = {
            { "CATEGORIES", "categories.txt" },
            { "FUNDS", "funds.txt" },
            { "ACCOUNTS", "accounts.txt" },
            { "RECURRING PAYMENTS", "recurring_payments.txt" },
            { "LOANS", "loans.txt" },
            { "TRANSACTIONS", "transactions.txt" },
            { "BILL PAYMENTS", "bill_payments.txt" },
            { "COUNTERPARTY CATEGORIES", "counterparty_categories.txt" },
            { "MESSAGE PATTERNS", "message_patterns.txt" },
            { "BUDGETS", "budgets.txt" },
    };
    private static final String[] TABLES = {
            "transactions", "categories", "recurring_payments", "funds", "accounts", "counterparty_categories",
            "loans", "message_patterns", "budgets", "budget_categories", "bill_payments" };

    // How older backups marked the rows of the accounts file that were accounts; the rest were funds
    private static final String OLD_ACCOUNT_TYPE = "REAL";

    private final AppDatabase database;
    private final SimpleDateFormat dateFormat;
    private boolean restoredMappings;
    private boolean restoredPatterns;

    public BackupDataLoader(Context context) {
        this(context, AppDatabase.getDatabase(context));
    }

    BackupDataLoader(Context context, AppDatabase database) {
        this.database = database;
        // How Date.toString() wrote dates before the current format
        this.dateFormat = new SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy", Locale.US);
    }

    /****************************************************************************************************/
    // Writing

    /** The text of one backup file; {@link #loadSingleEntityFromFile} reads it back. */
    static String entityText(String entityName, AppDatabase database) {
        StringBuilder data = new StringBuilder(FORMAT_LINE).append("\n---\n");
        switch (entityName) {
            case "CATEGORIES":
                for (Category c : database.categoryDao().getAllCategoriesSync()) {
                    put(data, "ID", c.getId());
                    put(data, "Name", c.getName());
                    put(data, "Type", c.getType());
                    put(data, "Is Default", c.isDefault());
                    put(data, "Sort Order", c.getSortOrder());
                    data.append("---\n");
                }
                break;
            case "RECURRING PAYMENTS":
                for (RecurringPayment p : database.recurringPaymentDao().getAllRecurringPaymentsSync()) {
                    put(data, "ID", p.getId());
                    put(data, "Name", p.getName());
                    put(data, "Amount", p.getAmount());
                    put(data, "Due Day", p.getDueDay());
                    put(data, "Expiry Date", p.getExpiryDate());
                    put(data, "Is Completed", p.isCompleted());
                    put(data, "Last Completed Date", p.getLastCompletedDate());
                    put(data, "From Fund ID", p.getFromFundId());
                    put(data, "To Fund ID", p.getToFundId());
                    put(data, "Currency", p.getCurrency());
                    put(data, "Start Period", p.getStartPeriod());
                    data.append("---\n");
                }
                break;
            case "FUNDS":
                for (Fund f : database.fundDao().getAllFundsSync()) {
                    put(data, "ID", f.getId());
                    put(data, "Name", f.getName());
                    put(data, "Description", f.getDescription());
                    put(data, "Is Default", f.isDefault());
                    put(data, "Sort Order", f.getSortOrder());
                    put(data, "Target", f.getTarget());
                    put(data, "Target Date", f.getTargetDate());
                    data.append("---\n");
                }
                break;
            case "ACCOUNTS":
                for (Account a : database.accountDao().getAllAccountsSync()) {
                    put(data, "ID", a.getId());
                    put(data, "Name", a.getName());
                    put(data, "Account Number", a.getAccountNumber());
                    put(data, "Bank", a.getBank());
                    put(data, "Expiry Date", a.getExpiryDate());
                    put(data, "Description", a.getDescription());
                    put(data, "Is Default", a.isDefault());
                    put(data, "Sort Order", a.getSortOrder());
                    put(data, "Currency", a.getCurrency());
                    data.append("---\n");
                }
                break;
            case "LOANS":
                for (Loan l : database.loanDao().getAllLoansSync()) {
                    put(data, "ID", l.getId());
                    put(data, "Person", l.getPerson());
                    put(data, "Amount", l.getAmount());
                    put(data, "Date", l.getDate());
                    put(data, "Note", l.getNote());
                    put(data, "Returned Manually", l.getReturnedManually());
                    put(data, "Closed Manually", l.isClosedManually());
                    put(data, "Closed Date", l.getClosedDate());
                    put(data, "Currency", l.getCurrency());
                    data.append("---\n");
                }
                break;
            case "TRANSACTIONS":
                for (Transaction t : database.transactionDao().getAllTransactionsSyncOrderByDateAsc()) {
                    put(data, "ID", t.getId());
                    put(data, "Amount", t.getAmount());
                    put(data, "Description", t.getDescription());
                    put(data, "Date", t.getDate());
                    put(data, "Transaction Type", t.getTransactionType());
                    put(data, "Receiver", t.getReceiverName());
                    put(data, "Category", t.getCategory());
                    put(data, "Is excluded from summary", t.isExcludedFromSummary());
                    put(data, "Fund ID", t.getFundId());
                    put(data, "Account ID", t.getAccountId());
                    put(data, "Linked Payment ID", t.getLinkedRecurringPaymentId());
                    put(data, "Loan ID", t.getLoanId());
                    put(data, "SMS Body", t.getSmsBody());
                    put(data, "SMS Sender", t.getSmsSender());
                    put(data, "SMS Hash", t.getSmsHash());
                    put(data, "Transfer Group", t.getTransferGroupId());
                    put(data, "Needs Review", t.isNeedsReview());
                    put(data, "Source", t.getSource());
                    put(data, "Dedup Key", t.getDedupKey());
                    put(data, "Match Key", t.getMatchKey());
                    put(data, "Message Time", t.getMessageTime());
                    put(data, "Currency", t.getCurrency());
                    data.append("---\n");
                }
                break;
            case "BILL PAYMENTS":
                for (BillPayment p : database.recurringPaymentDao().getAllPaymentsSync()) {
                    put(data, "Bill ID", p.getBillId());
                    put(data, "Period", p.getPeriod());
                    put(data, "Paid On", p.getPaidOn());
                    put(data, "Amount", p.getAmount());
                    put(data, "Currency", p.getCurrency());
                    put(data, "Transaction ID", p.getTransactionId());
                    put(data, "Skipped", p.isSkipped());
                    data.append("---\n");
                }
                break;
            case "COUNTERPARTY CATEGORIES":
                // The category goes by name as well, so a mapping finds it whatever its id becomes
                try (Cursor cursor = database.getOpenHelper().getReadableDatabase().query(
                        "SELECT m.counterparty, m.transactionType, c.id, c.name, c.type FROM counterparty_categories m "
                                + "JOIN categories c ON c.id = m.categoryId ORDER BY m.counterparty, m.transactionType")) {
                    while (cursor.moveToNext()) {
                        put(data, "Counterparty", cursor.getString(0));
                        put(data, "Transaction Type", cursor.getString(1));
                        put(data, "Category ID", cursor.getLong(2));
                        put(data, "Category Name", cursor.getString(3));
                        put(data, "Category Type", cursor.getString(4));
                        data.append("---\n");
                    }
                }
                break;
            case "BUDGETS":
                budgetText(data, database);
                break;
            case "MESSAGE PATTERNS":
                for (MessagePattern p : database.messagePatternDao().getAllSync()) {
                    put(data, "Name", p.getName());
                    put(data, "Pattern", p.getPattern());
                    put(data, "Transaction Type", p.getTransactionType());
                    put(data, "Counterparty", p.getCounterparty());
                    put(data, "Exclude From Summary", p.isExcludeFromSummary());
                    put(data, "Active", p.isActive());
                    put(data, "Sort Order", p.getSortOrder());
                    put(data, "Builtin Key", p.getBuiltinKey());
                    data.append("---\n");
                }
                break;
        }
        return data.toString();
    }

    private static void budgetText(StringBuilder data, AppDatabase database) {
        Map<Long, StringBuilder> categories = new LinkedHashMap<>();
        for (BudgetCategory category : database.budgetDao().getAllCategoriesSync()) {
            StringBuilder names = categories.computeIfAbsent(category.budgetId, id -> new StringBuilder());
            if (names.length() > 0) {
                names.append('\n');
            }
            names.append(category.category);
        }
        for (Budget b : database.budgetDao().getAllSync()) {
            StringBuilder names = categories.get(b.getId());
            put(data, "ID", b.getId());
            put(data, "Name", b.getName());
            put(data, "Amount", b.getAmount());
            put(data, "Currency", b.getCurrency());
            put(data, "Sort Order", b.getSortOrder());
            put(data, "Categories", names != null ? names.toString() : "");
            data.append("---\n");
        }
    }

    private static void put(StringBuilder data, String key, Object value) {
        data.append(key).append(": ").append(encode(value)).append('\n');
    }

    static String encode(Object value) {
        if (value == null) {
            return NULL;
        }
        if (value instanceof Date) {
            return String.valueOf(((Date) value).getTime());
        }
        // Double.toString never depends on the phone's language, and keeps every digit
        String text = value instanceof Double ? Double.toString((Double) value) : String.valueOf(value);
        return text.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r");
    }

    static String decode(String text) {
        if (text.equals(NULL)) {
            return null;
        }
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length()) {
                char next = text.charAt(++i);
                out.append(next == 'n' ? '\n' : next == 'r' ? '\r' : next);
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    /****************************************************************************************************/
    // Reading

    public void loadSingleEntityFromFile(File file, String sectionName) {
        try {
            load(file, sectionName);
        } catch (IOException e) {
            Log.e(TAG, "Error reading entity file " + sectionName, e);
        }
    }

    private void load(File file, String section) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line = reader.readLine();
            int format = formatOf(line);
            if (format > 0) {
                line = reader.readLine(); // The "---" that closes the format record
                line = reader.readLine();
            }
            List<String> record = new java.util.ArrayList<>();
            for (; line != null; line = reader.readLine()) {
                if (line.equals("---")) {
                    processEntity(section, record, format);
                    record = new java.util.ArrayList<>();
                } else {
                    record.add(line);
                }
            }
            if (!record.isEmpty()) {
                processEntity(section, record, format);
            }
        }
    }

    /** The format a file's first line names, or 0 for files from before formats were written. */
    private static int formatOf(String firstLine) {
        if (firstLine == null || !firstLine.startsWith(FORMAT_PREFIX)) {
            return 0;
        }
        try {
            return Integer.parseInt(firstLine.substring(FORMAT_PREFIX.length()).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void processEntity(String section, List<String> lines, int format) {
        Record record = new Record(lines, format);
        try {
            switch (section) {
                case "TRANSACTIONS":
                    processTransaction(record);
                    break;
                case "CATEGORIES":
                    processCategory(record);
                    break;
                case "RECURRING PAYMENTS":
                    processRecurringPayment(record);
                    break;
                case "BILL PAYMENTS":
                    processBillPayment(record);
                    break;
                case "FUNDS":
                    processFund(record);
                    break;
                case "ACCOUNTS":
                    if (record.format >= 3) {
                        processAccount(record);
                    } else {
                        processOldAccount(record);
                    }
                    break;
                case "COUNTERPARTY CATEGORIES":
                    processCounterpartyCategory(record);
                    break;
                case "LOANS":
                    processLoan(record);
                    break;
                case "MESSAGE PATTERNS":
                    processPattern(record);
                    break;
                case "BUDGETS":
                    processBudget(record);
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing entity in section " + section, e);
        }
    }

    /** One record's values. Older files wrote null as the word null and trimmed every value. */
    private final class Record {
        private final Map<String, String> values = new LinkedHashMap<>();
        private final int format; // 0 for files from before formats were written
        private final boolean escaped;

        Record(List<String> lines, int format) {
            this.format = format;
            this.escaped = format > 0;
            for (String line : lines) {
                String[] parts = line.split(": ", 2);
                if (parts.length != 2) {
                    continue;
                }
                String value = escaped ? decode(parts[1]) : parts[1].trim();
                values.put(parts[0].trim(), !escaped && value.equalsIgnoreCase("null") ? null : value);
            }
        }

        boolean has(String key) {
            return values.containsKey(key);
        }

        String text(String key) {
            return values.get(key);
        }

        Long number(String key) {
            String value = values.get(key);
            return value == null || value.isEmpty() ? null : Long.parseLong(value);
        }

        int integer(String key, int fallback) {
            Long value = number(key);
            return value != null ? value.intValue() : fallback;
        }

        boolean flag(String key) {
            return Boolean.parseBoolean(values.get(key));
        }

        double amount(String key) {
            String value = values.get(key);
            // Older backups formatted amounts in the phone's language, so 10.50 could be 10,50
            return value == null ? 0 : Double.parseDouble(value.replace(',', '.'));
        }

        Date date(String key) throws ParseException {
            String value = values.get(key);
            if (value == null || value.isEmpty()) {
                return null;
            }
            return escaped ? new Date(Long.parseLong(value)) : dateFormat.parse(value);
        }
    }

    private void processTransaction(Record record) throws ParseException {
        Transaction transaction = new Transaction();
        if (record.has("ID")) {
            transaction.setId(record.number("ID"));
        }
        transaction.setAmount(record.amount("Amount"));
        transaction.setDescription(record.text("Description"));
        transaction.setDate(record.date("Date"));
        transaction.setTransactionType(record.text("Transaction Type"));
        transaction.setReceiverName(record.text("Receiver"));
        transaction.setCategory(record.text("Category"));
        if (record.format >= 3) {
            transaction.setFundId(record.number("Fund ID"));
            transaction.setAccountId(record.number("Account ID"));
        } else {
            transaction.setFundId(record.number("Account id"));
            transaction.setAccountId(record.number("Real account id"));
        }
        transaction.setExcludedFromSummary(record.flag("Is excluded from summary"));
        transaction.setLinkedRecurringPaymentId(record.number("Linked Payment ID"));
        transaction.setLoanId(record.number("Loan ID"));
        transaction.setSmsBody(record.text("SMS Body"));
        transaction.setSmsSender(record.text("SMS Sender"));
        transaction.setSmsHash(record.text("SMS Hash"));
        transaction.setTransferGroupId(record.text("Transfer Group"));
        transaction.setNeedsReview(record.flag("Needs Review"));
        transaction.setCurrency(currencyOf(record));

        if (record.escaped) {
            transaction.setSource(record.text("Source"));
            transaction.setDedupKey(record.text("Dedup Key"));
            transaction.setMatchKey(record.text("Match Key"));
            transaction.setMessageTime(record.number("Message Time"));
        } else {
            // Only older backups need this; a newer one comes back exactly as it was saved
            Fund everyday = database.fundDao().getDefaultFundSync();
            if (transaction.getFundId() == null && !record.has("Real account id") && everyday != null) {
                transaction.setFundId(everyday.getId());
            }
            if (transaction.getTransactionType() != null && transaction.getTransactionType().trim().isEmpty()) {
                transaction.setTransactionType("DEBIT");
            }
            if (transaction.getReceiverName() == null) {
                transaction.setReceiverName("");
            }
            if (transaction.getDescription() == null) {
                transaction.setDescription("");
            }
            TransactionKeys.fillLegacyKeys(transaction);
        }

        if (transaction.getTransactionType() == null) {
            Log.i(TAG, "Skipping a transaction without a type");
            return;
        }
        database.transactionDao().insertTransaction(transaction);
    }

    private void processCategory(Record record) {
        String name = record.text("Name");
        String type = record.text("Type");
        if (name == null || name.isEmpty() || type == null || type.isEmpty()) {
            return;
        }
        Category category = new Category(name, type);
        category.setDefault(record.flag("Is Default"));
        category.setSortOrder(record.integer("Sort Order", 0));
        // Older backups did not keep category ids; nothing referred to them then
        if (record.escaped && record.has("ID")) {
            category.setId(record.number("ID"));
        }
        database.categoryDao().insertIfMissing(category);
    }

    private void processRecurringPayment(Record record) throws ParseException {
        String name = record.text("Name");
        if (name == null || name.trim().isEmpty()) {
            return;
        }
        RecurringPayment payment = new RecurringPayment(name, record.amount("Amount"),
                record.integer("Due Day", 1), record.date("Expiry Date"));
        if (record.has("ID")) {
            payment.setId(record.number("ID"));
        }
        payment.setCompleted(record.flag("Is Completed"));
        payment.setLastCompletedDate(record.date("Last Completed Date"));
        boolean fundKeys = record.format >= 3;
        payment.setFromFundId(record.number(fundKeys ? "From Fund ID" : "From Account ID"));
        payment.setToFundId(record.number(fundKeys ? "To Fund ID" : "To Account ID"));
        payment.setCurrency(currencyOf(record));
        payment.setStartPeriod(record.integer("Start Period", 0));
        database.recurringPaymentDao().insert(payment);
    }

    private void processBillPayment(Record record) throws ParseException {
        Long billId = record.number("Bill ID");
        int period = record.integer("Period", 0);
        if (billId == null || period == 0) {
            return;
        }
        BillPayment payment = new BillPayment();
        payment.setBillId(billId);
        payment.setPeriod(period);
        payment.setPaidOn(record.date("Paid On"));
        payment.setAmount(record.amount("Amount"));
        payment.setCurrency(currencyOf(record));
        payment.setTransactionId(record.number("Transaction ID"));
        payment.setSkipped(record.flag("Skipped"));
        database.recurringPaymentDao().insertPayment(payment);
    }

    private void processFund(Record record) throws ParseException {
        String name = record.text("Name");
        if (name == null || name.isEmpty()) {
            return;
        }
        insertFund(record, new Fund(name, record.text("Description")));
    }

    private void processAccount(Record record) throws ParseException {
        String name = record.text("Name");
        if (name == null || name.isEmpty()) {
            return;
        }
        Account account = new Account(name, orEmpty(record, "Account Number"), orEmpty(record, "Bank"),
                record.date("Expiry Date"), orEmpty(record, "Description"));
        account.setDefault(record.flag("Is Default"));
        account.setCurrency(currencyOf(record));
        account.setSortOrder(record.integer("Sort Order", 0));

        // The original id keeps transactions pointing at the right account
        Long originalId = record.number("ID");
        if (originalId != null) {
            if (database.accountDao().getAccountByIdSync(originalId) != null) {
                Log.i(TAG, "Account with ID " + originalId + " already exists");
                return;
            }
            account.setId(originalId);
        }
        database.accountDao().insert(account);
    }

    /**
     * Format 2 and older kept funds and accounts in one file. A row whose type is REAL is an account;
     * any other, including rows from before types, is a fund, and keeps its id either way.
     */
    private void processOldAccount(Record record) throws ParseException {
        if (OLD_ACCOUNT_TYPE.equals(record.text("Account Type"))) {
            processAccount(record);
            return;
        }
        String name = record.text("Name");
        if (name == null || name.isEmpty()) {
            return;
        }
        // Funds had a bank field once; it stays as the note when the fund had none of its own
        String description = orEmpty(record, "Description");
        if (description == null || description.isEmpty()) {
            String bank = orEmpty(record, "Bank");
            description = bank != null && !bank.isEmpty() ? bank : description;
        }
        insertFund(record, new Fund(name, description));
    }

    private void insertFund(Record record, Fund fund) throws ParseException {
        fund.setDefault(record.flag("Is Default"));
        fund.setSortOrder(record.integer("Sort Order", 0));
        fund.setTarget(record.text("Target") != null ? record.amount("Target") : null);
        fund.setTargetDate(record.date("Target Date"));

        // The original id keeps transactions, bills and transfers pointing at the right fund
        Long originalId = record.number("ID");
        if (originalId != null) {
            if (database.fundDao().getFundByIdSync(originalId) != null) {
                Log.i(TAG, "Fund with ID " + originalId + " already exists");
                return;
            }
            fund.setId(originalId);
        }
        database.fundDao().insert(fund);
    }

    // The original id keeps returns pointing at the right loan
    private void processLoan(Record record) throws ParseException {
        String person = record.text("Person");
        if (person == null || person.trim().isEmpty()) {
            return;
        }
        Loan loan = new Loan();
        if (record.has("ID")) {
            loan.setId(record.number("ID"));
        }
        loan.setPerson(person);
        loan.setAmount(record.amount("Amount"));
        loan.setDate(record.date("Date"));
        loan.setNote(record.text("Note"));
        loan.setReturnedManually(record.amount("Returned Manually"));
        loan.setClosedManually(record.flag("Closed Manually"));
        loan.setClosedDate(record.date("Closed Date"));
        loan.setCurrency(currencyOf(record));
        database.loanDao().insert(loan);
    }

    private String orEmpty(Record record, String key) {
        String value = record.text(key);
        return record.escaped || value != null ? value : "";
    }

    // Backups from before currencies were all in rupees
    private static String currencyOf(Record record) {
        String code = Currencies.normalize(record.text("Currency"));
        return code != null ? code : Currencies.LEGACY;
    }

    private void processCounterpartyCategory(Record record) {
        String counterparty = record.text("Counterparty");
        String type = record.text("Transaction Type");
        if (counterparty == null || type == null) {
            return;
        }
        Long categoryId = null;
        try (Cursor cursor = database.getOpenHelper().getReadableDatabase().query(
                "SELECT id FROM categories WHERE name = ? AND type = ?",
                new Object[] { record.text("Category Name"), record.text("Category Type") })) {
            if (cursor.moveToFirst()) {
                categoryId = cursor.getLong(0);
            }
        }
        if (categoryId == null) {
            categoryId = record.number("Category ID");
        }
        if (categoryId == null) {
            return;
        }
        database.getOpenHelper().getWritableDatabase().execSQL(
                "INSERT OR REPLACE INTO counterparty_categories (counterparty, transactionType, categoryId) VALUES (?, ?, ?)",
                new Object[] { counterparty, type, categoryId });
        restoredMappings = true;
    }

    // Categories are one per line, the way transactions name them
    private void processBudget(Record record) {
        String name = record.text("Name");
        if (name == null || name.trim().isEmpty()) {
            return;
        }
        Budget budget = new Budget();
        if (record.has("ID")) {
            budget.setId(record.number("ID"));
        }
        budget.setName(name);
        budget.setAmount(record.amount("Amount"));
        budget.setCurrency(currencyOf(record));
        budget.setSortOrder(record.integer("Sort Order", 0));
        long id = database.budgetDao().insert(budget);
        String categories = record.text("Categories");
        if (categories != null) {
            for (String category : categories.split("\n")) {
                if (!category.isEmpty()) {
                    database.budgetDao().insertCategory(new BudgetCategory(id, category));
                }
            }
        }
    }

    private void processPattern(Record record) {
        String name = record.text("Name");
        String regex = record.text("Pattern");
        String type = record.text("Transaction Type");
        if (name == null || regex == null || type == null) {
            return;
        }
        MessagePattern pattern = new MessagePattern(name, type, regex);
        pattern.setCounterparty(record.text("Counterparty"));
        pattern.setExcludeFromSummary(record.flag("Exclude From Summary"));
        pattern.setActive(record.flag("Active"));
        pattern.setSortOrder(record.integer("Sort Order", 0));
        pattern.setBuiltinKey(record.text("Builtin Key"));
        database.messagePatternDao().insert(pattern);
        restoredPatterns = true;
    }

    /****************************************************************************************************/
    // Restoring

    /**
     * Replaces everything on the phone with the given files, keyed by section. It is one
     * database transaction, so if anything fails the phone keeps the data it had.
     */
    void restore(Map<String, File> files) {
        database.runInTransaction(() -> {
            SupportSQLiteDatabase db = database.getOpenHelper().getWritableDatabase();
            for (String table : TABLES) {
                db.execSQL("DELETE FROM " + table);
            }
            restoredMappings = false;
            restoredPatterns = false;
            for (String[] entry : FILES) {
                File file = files.get(entry[0]);
                if (file != null) {
                    try {
                        load(file, entry[0]);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
            }
            finishRestore(database, !restoredMappings, !restoredPatterns);
        });
    }

    /** Fills in what an older backup does not carry. */
    static void finishRestore(AppDatabase restored) {
        finishRestore(restored, true, true);
    }

    static void finishRestore(AppDatabase restored, boolean rebuildMappings, boolean addBuiltInPatterns) {
        restored.runInTransaction(() -> {
            SupportSQLiteDatabase db = restored.getOpenHelper().getWritableDatabase();
            // Backups from before patterns were stored read messages with the shipped set
            if (addBuiltInPatterns) {
                MessagePattern.ensureBuiltIns(db);
            }
            Category.ensureDefaults(db);
            Fund.ensureDefault(db);
            Account.ensureDefault(db);
            Account.ensureCurrencies(db, Currencies.LEGACY);
            Transfers.linkLegacyPairs(db);
            // Backups from before bills kept their months get them from linked transactions, like the migration
            if (isEmpty(db, "bill_payments")) {
                BillPayment.backfill(db, Periods.current());
            }
            // Backups from before the mappings file are rebuilt from the transactions
            if (rebuildMappings) {
                CounterpartyCategory.rebuildFromTransactions(db);
            }
        });
    }

    private static boolean isEmpty(SupportSQLiteDatabase db, String table) {
        try (Cursor cursor = db.query("SELECT 1 FROM " + table + " LIMIT 1")) {
            return !cursor.moveToFirst();
        }
    }

    /****************************************************************************************************/
    // Google Drive

    /** Writes every table's file into one zip; the text is repetitive, so it shrinks several times over. */
    static void writeZip(AppDatabase database, File zip) throws IOException {
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zip))) {
            out.setLevel(Deflater.BEST_COMPRESSION);
            for (String[] entry : FILES) {
                out.putNextEntry(new ZipEntry(entry[1]));
                out.write(entityText(entry[0], database).getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
    }

    /** Unpacks a backup zip into {@code dir}, keyed by section. Names it does not know are left out. */
    static Map<String, File> readZip(File zip, File dir) throws IOException {
        Map<String, String> sections = new HashMap<>();
        for (String[] entry : FILES) {
            sections.put(entry[1], entry[0]);
        }
        Map<String, File> files = new HashMap<>();
        try (ZipInputStream in = new ZipInputStream(new FileInputStream(zip))) {
            byte[] buffer = new byte[8192];
            for (ZipEntry entry; (entry = in.getNextEntry()) != null; ) {
                String section = sections.get(entry.getName());
                if (section == null || entry.isDirectory()) {
                    continue;
                }
                // The name is one of ours, so the file cannot land outside the cache
                File file = new File(dir, "restore_" + entry.getName());
                files.put(section, file);
                try (FileOutputStream out = new FileOutputStream(file)) {
                    for (int read; (read = in.read(buffer)) != -1; ) {
                        out.write(buffer, 0, read);
                    }
                }
            }
        }
        return files;
    }

    /** One backup, restore or clean-up at a time: they share the cache files and the Drive folder. */
    private static final Object DRIVE_LOCK = new Object();

    /**
     * Uploads one zip, which appears on Drive whole or not at all; a backup that fails is deleted.
     * After it, only the newest {@value GoogleDriveService#KEEP_BACKUPS} backups are kept. Blocks,
     * so call it off the main thread; returns what to tell the user.
     */
    public static String backUp(Context context, AppDatabase database) throws IOException {
        synchronized (DRIVE_LOCK) {
            GoogleDriveService driveService = GoogleDriveService.connect(context);
            File zip = new File(context.getCacheDir(), GoogleDriveService.BACKUP_ZIP);
            try {
                writeZip(database, zip);
                String folderId = driveService.createBackupFolder();
                try {
                    driveService.uploadFile(folderId, zip, "application/zip");
                } catch (IOException | RuntimeException e) {
                    try {
                        driveService.delete(folderId);
                    } catch (IOException deleteError) {
                        Log.e(TAG, "Could not remove the unfinished backup", deleteError);
                    }
                    throw e;
                }
                BackupScheduler.recordBackup(context, System.currentTimeMillis());

                // The backup is safe by now, so a failure here only leaves extra old copies
                String pruned = "";
                try {
                    int count = driveService.pruneBackups(GoogleDriveService.KEEP_BACKUPS);
                    if (count > 0) {
                        pruned = ", removed " + count + " old backup(s)";
                    }
                } catch (IOException | RuntimeException e) {
                    Log.e(TAG, "Could not remove old backups", e);
                }
                return "Backup completed successfully" + pruned;
            } finally {
                zip.delete();
            }
        }
    }

    /**
     * Downloads the newest finished backup in full before anything on the phone changes, then
     * replaces the phone's data with it in one transaction. Blocks; returns what to tell the user.
     */
    public static String restoreLatest(Context context) throws IOException {
        synchronized (DRIVE_LOCK) {
            GoogleDriveService driveService = GoogleDriveService.connect(context);
            Map<String, File> files = new HashMap<>();
            File zip = new File(context.getCacheDir(), "restore_" + GoogleDriveService.BACKUP_ZIP);
            try {
                GoogleDriveService.BackupFolder backup = driveService.findLatestBackup();
                if (backup == null) {
                    throw new IOException("No complete backup found");
                }
                String zipId = backup.files.get(GoogleDriveService.BACKUP_ZIP);
                if (zipId != null) {
                    driveService.downloadFile(zipId, zip);
                    files.putAll(readZip(zip, context.getCacheDir()));
                } else {
                    // Backups from before the zip hold one file per table
                    for (String[] entry : FILES) {
                        String fileId = backup.files.get(entry[1]);
                        if (fileId != null) {
                            File file = new File(context.getCacheDir(), "restore_" + entry[1]);
                            files.put(entry[0], file);
                            driveService.downloadFile(fileId, file);
                        }
                    }
                }
                try {
                    new BackupDataLoader(context).restore(files);
                } catch (UncheckedIOException e) {
                    throw e.getCause();
                }
                return "Restore completed successfully";
            } finally {
                for (File file : files.values()) {
                    file.delete();
                }
                zip.delete();
            }
        }
    }

    /** Keeps only the newest backup on Drive. Blocks; returns what to tell the user. */
    public static String clearOldBackups(Context context) throws IOException {
        synchronized (DRIVE_LOCK) {
            return GoogleDriveService.connect(context).deleteOldBackups();
        }
    }

    /** When the newest finished backup on Drive was made, or 0 if there is none. Blocks. */
    public static long latestBackupTime(Context context) throws IOException {
        GoogleDriveService.BackupFolder latest = GoogleDriveService.connect(context).findLatestBackup();
        return latest != null ? latest.createdTime : 0;
    }

    /** Earlier versions left a plain text copy of the database here on every start. */
    static void deleteOldLocalExports(Context context) {
        File[] exports = new File(context.getFilesDir(), "database_exports").listFiles();
        if (exports != null) {
            for (File export : exports) {
                export.delete();
            }
            new File(context.getFilesDir(), "database_exports").delete();
        }
    }
}
