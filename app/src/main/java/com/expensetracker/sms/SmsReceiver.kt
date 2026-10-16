package com.expensetracker.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

/** Wakes up for every incoming SMS and queues an import when it comes from a bank-style sender. */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.any { SmsParser.isTransactionalSender(it.originatingAddress.orEmpty()) }) {
            SyncScheduler.onNewMessage(context)
        }
    }
}
