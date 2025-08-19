package com.hereliesaz.mademedance

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

// ... (imports)

class NLService : NotificationListenerService() {
    // ... (companion object)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (isMusicPlayerNotification(sbn)) {
            parseNotification(sbn)
        }
    }

    private fun isMusicPlayerNotification(sbn: StatusBarNotification): Boolean {
        val packageName = sbn.packageName
        val category = sbn.notification.category

        return (packageName.contains("music") || packageName.contains("audio")) &&
                (category == Notification.CATEGORY_TRANSPORT || category == null) // null for older Android versions
    }

    private fun parseNotification(sbn: StatusBarNotification) {
        try {
            val extras = sbn.notification.extras
            val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
            val artist = extras.getString(Notification.EXTRA_TEXT) ?: ""

            if (title.isNotEmpty() && artist.isNotEmpty()) {
                // ... (broadcast song info)
            }
        } catch (e: Exception) {
            Log.e("NLService", "Error parsing notification", e)
        }
    }
}
