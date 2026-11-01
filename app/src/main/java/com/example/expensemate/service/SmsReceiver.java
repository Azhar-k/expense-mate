package com.example.expensemate.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;

/** Declared in the manifest, so Android starts the app for a new SMS even when it is not running. */
public class SmsReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) {
            return;
        }
        SmsMessage[] messages = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if (messages == null) {
            return;
        }
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

        if (sender != null) {
            PendingResult pending = goAsync();
            MessageCapture.handleSms(context, fullMessage.toString(), sender, pending::finish);
        }
    }
}
