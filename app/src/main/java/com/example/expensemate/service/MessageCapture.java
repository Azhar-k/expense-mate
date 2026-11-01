package com.example.expensemate.service;

import android.app.Application;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Telephony;
import android.util.Log;

import com.example.expensemate.data.TransactionKeys;
import com.example.expensemate.util.MmsReader;
import com.example.expensemate.util.SmsTransactionHandler;
import com.example.expensemate.viewmodel.TransactionViewModel;

import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Turns new messages into transactions, one at a time, for whichever component woke the app. */
final class MessageCapture {
    private static final String TAG = "MessageCapture";
    private static final String PREFS_NAME = "SmsMonitorPrefs";
    private static final String PREF_LAST_MMS_ID = "lastMmsId";
    private static final String PREF_LAST_SMS_TIME = "lastSmsTime";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private static TransactionViewModel viewModel;

    private MessageCapture() {
    }

    /** Called on the main thread by every entry point below. */
    private static TransactionViewModel viewModel(Context context) {
        if (viewModel == null) {
            viewModel = new TransactionViewModel((Application) context.getApplicationContext());
        }
        return viewModel;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** Runs the work off the main thread and always calls done, so a wake-up is never left open. */
    private static void run(Runnable work, Runnable done) {
        EXECUTOR.execute(() -> {
            try {
                work.run();
            } catch (Exception e) {
                Log.e(TAG, "Error capturing messages", e);
            } finally {
                done.run();
            }
        });
    }

    static void handleSms(Context context, String body, String sender, Runnable done) {
        Context app = context.getApplicationContext();
        TransactionViewModel viewModel = viewModel(app);
        run(() -> {
            SmsTransactionHandler.handleSms(body, sender, viewModel, null, TransactionKeys.SOURCE_SMS_BROADCAST);
            prefs(app).edit().putLong(PREF_LAST_SMS_TIME, System.currentTimeMillis()).apply();
        }, done);
    }

    /** Picks up whatever arrived while the app could not be woken, such as after a force stop. */
    static void catchUp(Context context, Runnable done) {
        Context app = context.getApplicationContext();
        TransactionViewModel viewModel = viewModel(app);
        run(() -> {
            catchUpOnSms(app, viewModel);
            processNewMmsRows(app, viewModel);
        }, done);
    }

    static void processNewMms(Context context, Runnable done) {
        Context app = context.getApplicationContext();
        TransactionViewModel viewModel = viewModel(app);
        run(() -> processNewMmsRows(app, viewModel), done);
    }

    /**
     * Processes inbox SMS newer than the last one seen. The first run only records the
     * current time, so older messages stay with the manual scan.
     */
    private static void catchUpOnSms(Context context, TransactionViewModel viewModel) {
        SharedPreferences prefs = prefs(context);
        long now = System.currentTimeMillis();
        long lastSmsTime = prefs.getLong(PREF_LAST_SMS_TIME, -1);
        if (lastSmsTime < 0) {
            prefs.edit().putLong(PREF_LAST_SMS_TIME, now).apply();
            return;
        }

        try (Cursor cursor = context.getContentResolver().query(
                Telephony.Sms.CONTENT_URI,
                new String[] { Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE },
                Telephony.Sms.TYPE + " = ? AND " + Telephony.Sms.DATE + " > ?",
                new String[] { String.valueOf(Telephony.Sms.MESSAGE_TYPE_INBOX), String.valueOf(lastSmsTime) },
                Telephony.Sms.DATE + " ASC")) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    SmsTransactionHandler.handleSms(cursor.getString(1), cursor.getString(0), viewModel,
                            new Date(cursor.getLong(2)), TransactionKeys.SOURCE_SMS_SCAN);
                }
            }
            prefs.edit().putLong(PREF_LAST_SMS_TIME, now).apply();
        }
    }

    /**
     * Processes every RCS inbox row newer than the last one handled. The first run only
     * records the newest ID, so older messages stay with the manual scan.
     */
    private static void processNewMmsRows(Context context, TransactionViewModel viewModel) {
        ContentResolver resolver = context.getContentResolver();
        SharedPreferences prefs = prefs(context);
        long lastMmsId = prefs.getLong(PREF_LAST_MMS_ID, -1);

        try (Cursor cursor = resolver.query(
                Uri.parse("content://mms"),
                new String[] { "_id", "date" },
                "m_type = ? AND msg_box = ? AND _id > ?",
                new String[] { "132", "1", String.valueOf(lastMmsId) }, // RCS inbox messages
                "_id ASC")) {
            if (cursor == null) {
                return;
            }
            if (lastMmsId < 0) {
                if (cursor.moveToLast()) {
                    prefs.edit().putLong(PREF_LAST_MMS_ID, cursor.getLong(0)).apply();
                }
                return;
            }
            while (cursor.moveToNext()) {
                long mmsId = cursor.getLong(0);
                // The parts are written after the row; the next change event retries from here.
                if (!MmsReader.hasParts(resolver, mmsId)) {
                    return;
                }
                String messageBody = MmsReader.getText(resolver, mmsId);
                if (!messageBody.isEmpty()) {
                    SmsTransactionHandler.handleSms(messageBody, MmsReader.getSender(resolver, mmsId),
                            viewModel, new Date(cursor.getLong(1) * 1000),
                            TransactionKeys.SOURCE_MMS_OBSERVER);
                }
                prefs.edit().putLong(PREF_LAST_MMS_ID, mmsId).apply();
            }
        }
    }
}
