package com.example.expensemate.ui.sms;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Telephony;
import android.util.Log;

import com.example.expensemate.data.TransactionKeys;
import com.example.expensemate.util.MmsReader;
import com.example.expensemate.util.ScanTriage;
import com.example.expensemate.util.SmsTransactionHandler;
import com.example.expensemate.viewmodel.TransactionViewModel;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Reads the SMS and RCS inboxes for a date range and records what it recognises. */
final class SmsScanner {
    private static final String TAG = "SmsScanner";
    private static final String NO_MATCH = "No transaction pattern matched in SMS";
    private static final String DUPLICATE = "Duplicate transaction detected";

    static final class Message {
        final String body;
        final String sender;
        final Date date;

        Message(String body, String sender, Date date) {
            this.body = body;
            this.sender = sender;
            this.date = date;
        }
    }

    static final class Result {
        int added;
        int existing;
        int ignored;
        int failed;
        final List<Message> notUnderstood = new ArrayList<>();
    }

    private SmsScanner() {
    }

    /** Runs on the calling thread; do not call from the main thread. */
    static Result scan(ContentResolver resolver, long fromMillis, long toMillis, TransactionViewModel viewModel) {
        Result result = new Result();
        scanRcs(resolver, fromMillis, toMillis, viewModel, result);
        scanSms(resolver, fromMillis, toMillis, viewModel, result);
        Log.i(TAG, "Scan finished. Added " + result.added + ", already recorded " + result.existing
                + ", not understood " + result.notUnderstood.size() + ", failed " + result.failed);
        return result;
    }

    private static void scanRcs(ContentResolver resolver, long fromMillis, long toMillis,
            TransactionViewModel viewModel, Result result) {
        // RCS business messages are stored with m_type 132; dates are in seconds here
        String selection = "m_type = ? AND msg_box = ? AND date >= ? AND date <= ?";
        String[] args = { "132", "1", String.valueOf(fromMillis / 1000), String.valueOf(toMillis / 1000) };
        try (Cursor cursor = resolver.query(Uri.parse("content://mms"), new String[] { "_id", "date" }, selection,
                args, "date DESC")) {
            if (cursor == null) {
                return;
            }
            while (cursor.moveToNext()) {
                long id = cursor.getLong(0);
                Date date = new Date(cursor.getLong(1) * 1000);
                String body = MmsReader.getText(resolver, id);
                if (body != null && !body.isEmpty()) {
                    handle(new Message(body, MmsReader.getSender(resolver, id), date),
                            TransactionKeys.SOURCE_MMS_SCAN, viewModel, result);
                }
            }
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            Log.e(TAG, "Could not read RCS messages", e);
        }
    }

    private static void scanSms(ContentResolver resolver, long fromMillis, long toMillis,
            TransactionViewModel viewModel, Result result) {
        String[] projection = { Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE };
        String selection = Telephony.Sms.TYPE + " = ? AND " + Telephony.Sms.DATE + " >= ? AND "
                + Telephony.Sms.DATE + " <= ?";
        String[] args = { String.valueOf(Telephony.Sms.MESSAGE_TYPE_INBOX), String.valueOf(fromMillis),
                String.valueOf(toMillis) };
        try (Cursor cursor = resolver.query(Uri.parse("content://sms/"), projection, selection, args,
                Telephony.Sms.DATE + " DESC")) {
            if (cursor == null) {
                return;
            }
            while (cursor.moveToNext()) {
                handle(new Message(cursor.getString(1), cursor.getString(0), new Date(cursor.getLong(2))),
                        TransactionKeys.SOURCE_SMS_SCAN, viewModel, result);
            }
        }
    }

    private static void handle(Message message, String source, TransactionViewModel viewModel, Result result) {
        SmsTransactionHandler.TransactionResult outcome = SmsTransactionHandler.handleSms(message.body,
                message.sender, viewModel, message.date, source);
        if (outcome.success) {
            result.added++;
        } else if (DUPLICATE.equals(outcome.reason)) {
            result.existing++;
        } else if (NO_MATCH.equals(outcome.reason)) {
            if (ScanTriage.looksLikeTransaction(message.body)) {
                result.notUnderstood.add(message);
            } else {
                result.ignored++;
            }
        } else {
            result.failed++;
            result.notUnderstood.add(message);
        }
    }
}
