package com.example.expensemate.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.util.Log;

public class SmsReceiver extends BroadcastReceiver {
    private static final String TAG = "SmsReceiver";

    public SmsReceiver() {
        callback = (x, y)->{
        };
    }
    private final SmsCallback callback;

    public interface SmsCallback {
        void onSmsReceived(String smsBody, String sender);
    }

    public SmsReceiver(SmsCallback callback) {
        this.callback = callback;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) {
            SmsMessage[] messages = Telephony.Sms.Intents.getMessagesFromIntent(intent);
            if (messages != null) {
                StringBuilder fullMessage = new StringBuilder();
                String sender = null;
                
                // Concatenate all message parts
                for (SmsMessage message : messages) {
                    if (sender == null) {
                        sender = message.getOriginatingAddress();
                    }
                    String body = message.getMessageBody();
                    if (body != null) {
                        fullMessage.append(body);
                    }
                }
                
                String completeBody = fullMessage.toString();
                Log.d(TAG, "Received SMS from: " + sender);
                Log.d(TAG, "Full SMS body length: " + (completeBody != null ? completeBody.length() : 0));
                Log.d(TAG, "Full SMS body: [" + completeBody + "]");
                
                if (sender != null) {
                    callback.onSmsReceived(completeBody, sender);
                }
            }
        }
    }
} 