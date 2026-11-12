package com.example.expensemate.data;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;

import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.expensemate.service.GoogleDriveService;
import com.example.expensemate.service.GoogleSignInHelper;
import com.example.expensemate.util.Currencies;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Backs up to Google Drive as one text file per table and restores from them.
 *
 * Each record is a block of "Key: value" lines ending with "---". Files written by this version
 * start with a {@value #FORMAT_LINE} record: their values are escaped, so text can hold line
 * breaks, null is written as \N, and dates are milliseconds. Files without that line come from
 * older versions and are read the way those versions wrote them.
 */
public class BackupDataLoader {
    private static final String TAG = "BackupDataLoader";
    static final String FORMAT_LINE = "Format: 2";
    private static final String NULL = "\\N";

    /** Restore order: transactions refer to accounts, bills and loans, mappings to categories. */
    static final String[][] FILES = {
            { "CATEGORIES", "categories.txt" },
            { "RECURRING PAYMENTS", "recurring_payments.txt" },
            { "ACCOUNTS", "accounts.txt" },
            { "LOANS", "loans.txt" },
            { "TRANSACTIONS", "transactions.txt" },
            { "COUNTERPARTY CATEGORIES", "counterparty_categories.txt" },
            { "MESSAGE PATTERNS", "message_patterns.txt" },
    };
    private static final String[] TABLES = {
            "transactions", "categories", "recurring_payments", "accounts", "counterparty_categories", "loans",
            "message_patterns" };

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
                    put(data, "From Account ID", p.getFromAccountId());
                    put(data, "To Account ID", p.getToAccountId());
                    put(data, "Currency", p.getCurrency());
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
                    put(data, "Account Type", a.getType());
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
                    put(data, "Account id", t.getAccountId());
                    put(data, "Real account id", t.getRealAccountId());
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
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line = reader.readLine();
            boolean escaped = FORMAT_LINE.equals(line);
            if (escaped) {
                line = reader.readLine(); // The "---" that closes the format record
                line = reader.readLine();
            }
            List<String> record = new java.util.ArrayList<>();
            for (; line != null; line = reader.readLine()) {
                if (line.equals("---")) {
                    processEntity(section, record, escaped);
                    record = new java.util.ArrayList<>();
                } else {
                    record.add(line);
                }
            }
            if (!record.isEmpty()) {
                processEntity(section, record, escaped);
            }
        }
    }

    private void processEntity(String section, List<String> lines, boolean escaped) {
        Record record = new Record(lines, escaped);
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
                case "ACCOUNTS":
                    processAccount(record);
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
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing entity in section " + section, e);
        }
    }

    /** One record's values. Older files wrote null as the word null and trimmed every value. */
    private final class Record {
        private final Map<String, String> values = new LinkedHashMap<>();
        private final boolean escaped;

        Record(List<String> lines, boolean escaped) {
            this.escaped = escaped;
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
        transaction.setAccountId(record.number("Account id"));
        transaction.setRealAccountId(record.number("Real account id"));
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
            if (transaction.getAccountId() == null && !record.has("Real account id")) {
                transaction.setAccountId(database.accountDao().getDefaultAccountSync().getId());
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
        payment.setFromAccountId(record.number("From Account ID"));
        payment.setToAccountId(record.number("To Account ID"));
        payment.setCurrency(currencyOf(record));
        database.recurringPaymentDao().insert(payment);
    }

    private void processAccount(Record record) throws ParseException {
        String name = record.text("Name");
        if (name == null || name.isEmpty()) {
            return;
        }
        Account account = new Account(name, orEmpty(record, "Account Number"), orEmpty(record, "Bank"),
                record.date("Expiry Date"), orEmpty(record, "Description"));
        account.setDefault(record.flag("Is Default"));
        account.setType(Account.TYPE_REAL.equals(record.text("Account Type")) ? Account.TYPE_REAL : Account.TYPE_VIRTUAL);
        account.setCurrency(account.isReal() ? currencyOf(record) : null);
        account.setSortOrder(record.integer("Sort Order", 0));

        // The original id keeps transactions and bills pointing at the right account
        Long originalId = record.number("ID");
        if (originalId != null) {
            for (Account existing : database.accountDao().getAllAccountsSync()) {
                if (existing.getId() == originalId) {
                    Log.i(TAG, "Account with ID " + originalId + " already exists");
                    return;
                }
            }
            account.setId(originalId);
        }
        database.accountDao().insert(account);
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
            Account.ensureDefaultRealAccount(db);
            Account.ensureRealAccountCurrencies(db, Currencies.LEGACY);
            Transfers.linkLegacyPairs(db);
            // Backups from before the mappings file are rebuilt from the transactions
            if (rebuildMappings) {
                CounterpartyCategory.rebuildFromTransactions(db);
            }
        });
    }

    /****************************************************************************************************/
    // Google Drive

    /** Uploads every file, then a marker; a backup that stops partway is deleted, not left looking finished. */
    public static void exportDatabaseDataToGoogleDrive(Context context, AppDatabase database,
            GoogleDriveService.DriveCallback callback) {
        new Thread(() -> {
            GoogleDriveService driveService = new GoogleDriveService(context);
            try {
                GoogleSignInHelper signInHelper = new GoogleSignInHelper(context);
                if (!signInHelper.isSignedIn()) {
                    callback.onError("User not signed in to Google Drive");
                    return;
                }
                driveService.initializeDriveService(signInHelper.getAccessToken());

                String folderId = driveService.createBackupFolder();
                try {
                    for (String[] entry : FILES) {
                        upload(context, driveService, folderId, entry[1], entityText(entry[0], database));
                    }
                    upload(context, driveService, folderId, GoogleDriveService.COMPLETE_MARKER,
                            "Backup finished " + new Date() + "\n");
                } catch (IOException | RuntimeException e) {
                    try {
                        driveService.delete(folderId);
                    } catch (IOException deleteError) {
                        Log.e(TAG, "Could not remove the unfinished backup", deleteError);
                    }
                    throw e;
                }
                callback.onSuccess("Backup completed successfully");
            } catch (Exception e) {
                Log.e(TAG, "Error backing up to Google Drive", e);
                callback.onError("Backup failed: " + e.getMessage());
            } finally {
                driveService.shutdown();
            }
        }).start();
    }

    private static void upload(Context context, GoogleDriveService driveService, String folderId, String fileName,
            String text) throws IOException {
        File file = new File(context.getCacheDir(), fileName);
        try {
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(text);
            }
            driveService.uploadFile(folderId, file, "text/plain");
        } finally {
            file.delete();
        }
    }

    /** Downloads the newest finished backup in full before anything on the phone changes. */
    public static void loadBackupDataFromGoogleDrive(Context context, GoogleDriveService.DriveCallback callback) {
        new Thread(() -> {
            GoogleDriveService driveService = new GoogleDriveService(context);
            Map<String, File> files = new HashMap<>();
            try {
                GoogleSignInHelper signInHelper = new GoogleSignInHelper(context);
                if (!signInHelper.isSignedIn()) {
                    callback.onError("User not signed in to Google Drive");
                    return;
                }
                driveService.initializeDriveService(signInHelper.getAccessToken());

                GoogleDriveService.BackupFolder backup = driveService.findLatestBackup();
                if (backup == null) {
                    callback.onError("No complete backup found");
                    return;
                }
                for (String[] entry : FILES) {
                    String fileId = backup.files.get(entry[1]);
                    if (fileId != null) {
                        File file = new File(context.getCacheDir(), "restore_" + entry[1]);
                        files.put(entry[0], file);
                        driveService.downloadFile(fileId, file);
                    }
                }
                new BackupDataLoader(context).restore(files);
                callback.onSuccess("Restore completed successfully");
            } catch (Exception e) {
                Log.e(TAG, "Error restoring from Google Drive", e);
                callback.onError("Restore failed, nothing was changed: " + e.getMessage());
            } finally {
                for (File file : files.values()) {
                    file.delete();
                }
                driveService.shutdown();
            }
        }).start();
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
