//package com.example.expensemate.service;
//
//import android.app.Notification;
//import android.content.ContentResolver;
//import android.database.Cursor;
//import android.net.Uri;
//import android.os.Bundle;
//import android.service.notification.NotificationListenerService;
//import android.service.notification.StatusBarNotification;
//import android.util.Log;
//
//import com.example.expensemate.data.Transaction;
//import com.example.expensemate.util.SmsTransactionHandler;
//import com.example.expensemate.viewmodel.TransactionViewModel;
//
//import java.util.Date;
//import java.util.HashSet;
//import java.util.Set;
//import java.util.concurrent.ExecutorService;
//import java.util.concurrent.Executors;
//
///**
// * NotificationListenerService that captures RCS Business Messaging transactions.
// *
// * On this device, RCS business chats are displayed by Truecaller (com.truecaller).
// * The full message body is NOT stored in content://sms — it lives in Google Messages'
// * private database. We therefore parse the Truecaller notification extras directly.
// *
// * Truecaller RCS notification format:
// *   android.subText  = "Business chat from SBI CARDS"   → sender
// *   android.title    = "₹1"                             → amount
// *   android.text     = "•  SBI CARDS  •  Frontparking"  → sender • merchant
// */
//public class RcsNotificationListenerService extends NotificationListenerService {
//    private static final String TAG = "RcsNotifListener";
//
//    // Deduplication: track notification keys already processed this session
//    private final Set<String> processedKeys = new HashSet<>();
//
//    private TransactionViewModel transactionViewModel;
//    private ExecutorService executorService;
//
//    @Override
//    public void onCreate() {
//        super.onCreate();
//        Log.d(TAG, "=== RcsNotificationListenerService CREATED ===");
//        transactionViewModel = new TransactionViewModel(getApplication());
//        executorService = Executors.newSingleThreadExecutor();
//    }
//
//    @Override
//    public void onListenerConnected() {
//        super.onListenerConnected();
//        Log.d(TAG, "=== onListenerConnected: Service active ===");
//    }
//
//    @Override
//    public void onListenerDisconnected() {
//        super.onListenerDisconnected();
//        Log.w(TAG, "=== onListenerDisconnected ===");
//    }
//
//    @Override
//    public void onNotificationPosted(StatusBarNotification sbn) {
//        if (sbn == null) return;
//        String packageName = sbn.getPackageName();
//        if (packageName == null) return;
//
//        Notification notification = sbn.getNotification();
//        if (notification == null || notification.extras == null) return;
//
//        Bundle extras = notification.extras;
//
//        // ── Truecaller RCS Business Chat ──────────────────────────────────────────
//        if (packageName.equals("com.truecaller")) {
//            String subText = getExtra(extras, "android.subText");
//            if (subText != null && subText.startsWith("Business chat from ")) {
//
//                // Dedup by notification key + post time
//                String dedupeKey = sbn.getKey() + "|" + sbn.getPostTime();
//                if (processedKeys.contains(dedupeKey)) {
//                    Log.d(TAG, "Already processed notification key, skipping: " + dedupeKey);
//                    return;
//                }
//                processedKeys.add(dedupeKey);
//
//                String sender = subText.replace("Business chat from ", "").trim();
//                String title  = getExtra(extras, "android.title");  // e.g. "₹1"
//                String text   = getExtra(extras, "android.text");   // e.g. "•  SBI CARDS  •  Frontparking"
//                long postTime = sbn.getPostTime();
//
//                Log.d(TAG, "Truecaller RCS business chat detected");
//                Log.d(TAG, "  Sender  : [" + sender + "]");
//                Log.d(TAG, "  Title   : [" + title  + "]");
//                Log.d(TAG, "  Text    : [" + text   + "]");
//
//                // Step 1: try to find the full body in any SMS/MMS provider
//                executorService.execute(() -> {
//                    String body = findRecentSmsBody(sender, postTime);
//
//                    if (body != null) {
//                        Log.d(TAG, "Full SMS body found in provider, using it.");
//                        processBody(body, sender, new Date(postTime));
//                    } else {
//                        // Step 2: build a synthetic body from notification data and try to parse
//                        Log.d(TAG, "Full body not in provider. Parsing Truecaller notification directly.");
//                        parseTruecallerRcsNotification(sender, title, text, postTime);
//                    }
//                });
//                return;
//            }
//        }
//
//        // ── Standard messaging apps (Google Messages, Samsung Messages) ──────────
//        if (isStandardMessagingApp(packageName)) {
//            String sender  = getExtra(extras, Notification.EXTRA_TITLE);
//            String bigText = getExtra(extras, Notification.EXTRA_BIG_TEXT);
//            String text    = getExtra(extras, Notification.EXTRA_TEXT);
//            String body    = bigText != null ? bigText : text;
//            if (sender != null && body != null && !body.trim().isEmpty()) {
//                String dedupeKey = sbn.getKey() + "|" + sbn.getPostTime();
//                if (processedKeys.contains(dedupeKey)) return;
//                processedKeys.add(dedupeKey);
//                long postTime = sbn.getPostTime();
//                executorService.execute(() -> processBody(body, sender, new Date(postTime)));
//            }
//        }
//    }
//
//    // ─────────────────────────────────────────────────────────────────────────────
//    // Provider lookup: search ALL recent messages (last 90 s) regardless of address
//    // ─────────────────────────────────────────────────────────────────────────────
//
//    private String findRecentSmsBody(String sender, long postTime) {
//        long since = postTime - 90_000; // 90 seconds window
//
//        // 1. Try content://sms (both inbox and all)
//        String body = querySmsSince(Uri.parse("content://sms"), since, sender);
//        if (body != null) return body;
//
//        // Also try without address filter — log all recent entries for diagnostics
//        logAllRecentSms(since);
//
//        // 2. Try content://mms (various m_type values)
//        body = queryMmsSince(since);
//        return body;
//    }
//
//    private String querySmsSince(Uri uri, long sinceMs, String senderHint) {
//        ContentResolver cr = getContentResolver();
//        // First: filter by date only — no address filter — to see if any SMS arrived
//        try (Cursor c = cr.query(
//                uri,
//                new String[]{"_id", "address", "body", "date"},
//                "date >= ?",
//                new String[]{String.valueOf(sinceMs)},
//                "date DESC")) {
//
//            if (c == null || c.getCount() == 0) return null;
//            Log.d(TAG, "SMS query found " + c.getCount() + " recent rows:");
//            while (c.moveToNext()) {
//                long id   = c.getLong(c.getColumnIndexOrThrow("_id"));
//                String addr = c.getString(c.getColumnIndexOrThrow("address"));
//                String body = c.getString(c.getColumnIndexOrThrow("body"));
//                Log.d(TAG, "  SMS id=" + id + " addr=[" + addr + "] body=["
//                        + (body != null && body.length() > 60 ? body.substring(0, 60) + "..." : body) + "]");
//
//                // Accept if address resembles the sender or any inbox message arrived recently
//                if (addr != null && addr.toUpperCase().contains(senderHint.toUpperCase())) {
//                    return body;
//                }
//            }
//        } catch (Exception e) {
//            Log.e(TAG, "querySmsSince error: " + e.getMessage());
//        }
//        return null;
//    }
//
//    private String queryMmsSince(long sinceMs) {
//        ContentResolver cr = getContentResolver();
//        long sinceSecs = sinceMs / 1000;
//        try (Cursor c = cr.query(
//                Uri.parse("content://mms"),
//                new String[]{"_id", "date", "m_type", "msg_box"},
//                "date >= ? AND msg_box = 1",
//                new String[]{String.valueOf(sinceSecs)},
//                "date DESC")) {
//
//            if (c == null || c.getCount() == 0) {
//                Log.d(TAG, "No recent MMS entries found.");
//                return null;
//            }
//            Log.d(TAG, "MMS query found " + c.getCount() + " recent rows:");
//            while (c.moveToNext()) {
//                long id     = c.getLong(c.getColumnIndexOrThrow("_id"));
//                long mType  = c.getLong(c.getColumnIndexOrThrow("m_type"));
//                Log.d(TAG, "  MMS id=" + id + " m_type=" + mType);
//                String body = getMmsText(id);
//                if (body != null && !body.isEmpty()) {
//                    Log.d(TAG, "  MMS body=[" + (body.length() > 60 ? body.substring(0, 60) + "..." : body) + "]");
//                    return body;
//                }
//            }
//        } catch (Exception e) {
//            Log.e(TAG, "queryMmsSince error: " + e.getMessage());
//        }
//        return null;
//    }
//
//    private String getMmsText(long mmsId) {
//        try (Cursor c = getContentResolver().query(
//                Uri.parse("content://mms/part"),
//                new String[]{"ct", "text"},
//                "mid = ?",
//                new String[]{String.valueOf(mmsId)},
//                null)) {
//            if (c == null) return null;
//            StringBuilder sb = new StringBuilder();
//            while (c.moveToNext()) {
//                if ("text/plain".equals(c.getString(c.getColumnIndexOrThrow("ct")))) {
//                    String t = c.getString(c.getColumnIndexOrThrow("text"));
//                    if (t != null) sb.append(t);
//                }
//            }
//            return sb.toString();
//        } catch (Exception e) {
//            Log.e(TAG, "getMmsText error: " + e.getMessage());
//            return null;
//        }
//    }
//
//    private void logAllRecentSms(long sinceMs) {
//        try (Cursor c = getContentResolver().query(
//                Uri.parse("content://sms"),
//                new String[]{"_id", "address", "body", "date", "type"},
//                "date >= ?",
//                new String[]{String.valueOf(sinceMs)},
//                "date DESC")) {
//            if (c == null || c.getCount() == 0) {
//                Log.d(TAG, "DIAG: No SMS rows at all in last 90s.");
//                return;
//            }
//            Log.d(TAG, "DIAG: All SMS in last 90s (" + c.getCount() + " rows):");
//            while (c.moveToNext()) {
//                Log.d(TAG, "  id=" + c.getLong(0)
//                        + " type=" + c.getInt(4)
//                        + " addr=[" + c.getString(1) + "]"
//                        + " body=[" + (c.getString(2) != null && c.getString(2).length() > 40
//                        ? c.getString(2).substring(0, 40) + "..." : c.getString(2)) + "]");
//            }
//        } catch (Exception e) {
//            Log.e(TAG, "logAllRecentSms error: " + e.getMessage());
//        }
//    }
//
//    // ─────────────────────────────────────────────────────────────────────────────
//    // Parse Truecaller RCS notification directly when body is not in any provider
//    //
//    // Truecaller format:
//    //   title  = "₹1"                            → amount (rupee symbol + number)
//    //   text   = "•  SBI CARDS  •  Frontparking" → [sender] • [merchant]
//    //   subText= "Business chat from SBI CARDS"  → sender
//    //
//    // We reconstruct a body string that SmsTransactionHandler can parse,
//    // OR we build the Transaction directly if no pattern matches.
//    // ─────────────────────────────────────────────────────────────────────────────
//
//    private void parseTruecallerRcsNotification(String sender, String title, String text, long postTime) {
//        if (title == null || text == null) {
//            Log.w(TAG, "Truecaller RCS: title or text is null, cannot parse.");
//            return;
//        }
//
//        // Parse amount from title — remove ₹, commas, spaces
//        String amountStr = title.replaceAll("[₹,\\s]", "").trim();
//        double amount;
//        try {
//            amount = Double.parseDouble(amountStr);
//        } catch (NumberFormatException e) {
//            Log.w(TAG, "Truecaller RCS: could not parse amount from title=[" + title + "]");
//            return;
//        }
//
//        // Parse merchant from text: "•  SBI CARDS  •  Frontparking"
//        // Split on bullet/dot separators
//        String merchant = sender; // default
//        String[] parts = text.split("•");
//        if (parts.length >= 2) {
//            merchant = parts[parts.length - 1].trim();
//        }
//        if (merchant.isEmpty()) merchant = sender;
//
//        Log.d(TAG, "Parsed from Truecaller RCS | Amount=" + amount
//                + " | Sender=[" + sender + "] | Merchant=[" + merchant + "]");
//
//        // Build a synthetic SMS body and try SmsTransactionHandler patterns first
//        String syntheticBody = "Rs." + amount + " spent on your " + sender
//                + " at " + merchant + " on " + new java.text.SimpleDateFormat(
//                "dd-MM-yy", java.util.Locale.getDefault()).format(new Date(postTime))
//                + " via UPI (Ref No. 000000000000)";
//
//        Log.d(TAG, "Synthetic body: [" + syntheticBody + "]");
//        SmsTransactionHandler.TransactionResult result =
//                SmsTransactionHandler.handleSms(syntheticBody, sender, transactionViewModel, new Date(postTime));
//
//        if (result.success) {
//            Log.d(TAG, "✓ Transaction created via synthetic body match");
//            return;
//        }
//
//        // Pattern didn't match — create Transaction directly from parsed values
//        Log.d(TAG, "Synthetic body did not match patterns. Creating transaction directly.");
//        Transaction transaction = new Transaction(
//                amount,
//                "RCS: " + merchant,
//                new Date(postTime),
//                "DEBIT",
//                merchant,
//                text,   // raw notification text as body
//                sender
//        );
//        transactionViewModel.insertTransaction(transaction);
//        Log.d(TAG, "✓ Transaction inserted directly | Amount=" + amount
//                + " | Merchant=[" + merchant + "] | Sender=[" + sender + "]");
//    }
//
//    // ─────────────────────────────────────────────────────────────────────────────
//
//    private void processBody(String body, String sender, Date date) {
//        if (body == null || body.trim().isEmpty()) return;
//        Log.d(TAG, "Passing to SmsTransactionHandler | Sender=[" + sender
//                + "] | Body=[" + (body.length() > 60 ? body.substring(0, 60) + "..." : body) + "]");
//        SmsTransactionHandler.TransactionResult result =
//                SmsTransactionHandler.handleSms(body, sender, transactionViewModel, date);
//        if (result.success) {
//            Log.d(TAG, "✓ Transaction created from full SMS body");
//        } else {
//            Log.d(TAG, "✗ No transaction. Reason: " + result.reason);
//        }
//    }
//
//    private boolean isStandardMessagingApp(String packageName) {
//        return packageName.equals("com.google.android.apps.messaging")
//                || packageName.equals("com.samsung.android.messaging")
//                || packageName.equals("com.android.mms");
//    }
//
//    private String getExtra(Bundle extras, String key) {
//        CharSequence cs = extras.getCharSequence(key);
//        return cs != null ? cs.toString() : null;
//    }
//
//    @Override
//    public void onDestroy() {
//        super.onDestroy();
//        Log.d(TAG, "=== RcsNotificationListenerService DESTROYED ===");
//        if (executorService != null) {
//            executorService.shutdown();
//        }
//    }
//}
