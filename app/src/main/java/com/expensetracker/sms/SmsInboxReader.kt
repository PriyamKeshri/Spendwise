package com.expensetracker.sms

import android.content.Context
import android.provider.Telephony
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

data class InboxMessage(val id: Long, val sender: String, val body: String, val timestamp: Long)

class SmsInboxReader @Inject constructor(@ApplicationContext private val context: Context) {

    /**
     * Streams inbox messages received at or after [sinceMillis], oldest first, without loading the
     * whole inbox into memory. Requires READ_SMS.
     */
    suspend fun forEachSince(
        sinceMillis: Long,
        onMessage: suspend (message: InboxMessage, index: Int, total: Int) -> Unit,
    ) {
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
        )
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            projection,
            "${Telephony.Sms.DATE} >= ?",
            arrayOf(sinceMillis.toString()),
            "${Telephony.Sms.DATE} ASC",
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
            val addressColumn = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyColumn = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateColumn = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
            val total = cursor.count
            var index = 0
            while (cursor.moveToNext()) {
                val message = InboxMessage(
                    id = cursor.getLong(idColumn),
                    sender = cursor.getString(addressColumn).orEmpty(),
                    body = cursor.getString(bodyColumn).orEmpty(),
                    timestamp = cursor.getLong(dateColumn),
                )
                onMessage(message, index++, total)
            }
        }
    }
}
