package com.expensetracker.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

object SmsPermissions {
    val required = arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)

    fun canRead(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
}
