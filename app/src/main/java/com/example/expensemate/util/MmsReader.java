package com.example.expensemate.util;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

/**
 * Reads RCS business messages, which Google Messages mirrors into the MMS provider.
 * Rich cards hold their content in a JSON part and have no text/plain part.
 */
public final class MmsReader {
    private static final String TAG = "MmsReader";
    private static final String ADDRESS_TYPE_FROM = "137";

    private MmsReader() {
    }

    /** Returns the joined text parts, empty if there are none. */
    public static String getText(ContentResolver resolver, long mmsId) {
        StringBuilder body = new StringBuilder();
        try (Cursor cursor = resolver.query(
                Uri.parse("content://mms/part"),
                new String[] { "ct", "text" },
                "mid = ?",
                new String[] { String.valueOf(mmsId) },
                null)) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String text = cursor.getString(cursor.getColumnIndexOrThrow("text"));
                    if ("text/plain".equals(cursor.getString(cursor.getColumnIndexOrThrow("ct"))) && text != null) {
                        if (body.length() > 0) {
                            body.append(" ");
                        }
                        body.append(text);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting MMS text: " + e.getMessage(), e);
        }
        return body.toString();
    }

    /** False while the provider has the message row but has not written its parts yet. */
    public static boolean hasParts(ContentResolver resolver, long mmsId) {
        try (Cursor cursor = resolver.query(
                Uri.parse("content://mms/part"),
                new String[] { "_id" },
                "mid = ?",
                new String[] { String.valueOf(mmsId) },
                null)) {
            return cursor != null && cursor.getCount() > 0;
        } catch (Exception e) {
            Log.e(TAG, "Error checking MMS parts: " + e.getMessage(), e);
            return false;
        }
    }

    /** Returns the sender, such as an agent ending in @rbm.goog, or empty if unknown. */
    public static String getSender(ContentResolver resolver, long mmsId) {
        try (Cursor cursor = resolver.query(
                Uri.parse("content://mms/" + mmsId + "/addr"),
                new String[] { "address" },
                "type = ?",
                new String[] { ADDRESS_TYPE_FROM },
                null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String address = cursor.getString(0);
                return address != null ? address : "";
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting MMS sender: " + e.getMessage(), e);
        }
        return "";
    }
}
