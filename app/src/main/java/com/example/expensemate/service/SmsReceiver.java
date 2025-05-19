package com.example.expensemate.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;

public class SmsReceiver extends BroadcastReceiver {

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
                for (SmsMessage message : messages) {
                    String sender = message.getOriginatingAddress();
                    if (sender != null) {
                        callback.onSmsReceived(message.getMessageBody(), sender);
                    }
                }
            }
        }
    }
} 