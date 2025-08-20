package com.hereliesaz.mademedance

import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class NLService : NotificationListenerService() {

    companion object {
        const val ACTION_UPDATE_CURRENT_SONG = "com.hereliesaz.mademedance.UPDATE_CURRENT_SONG"
        const val EXTRA_SONG_TITLE = "com.hereliesaz.mademedance.SONG_TITLE"
        const val EXTRA_SONG_ARTIST = "com.hereliesaz.mademedance.SONG_ARTIST"
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // More robust check for music players
        if (sbn.notification.extras.containsKey(Notification.EXTRA_MEDIA_SESSION)) {
            parseNotification(sbn)
        }
    }

    private fun parseNotification(sbn: StatusBarNotification) {
        try {
            val extras = sbn.notification.extras
            val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
            val artist = extras.getString(Notification.EXTRA_TEXT) ?: ""

            if (title.isNotEmpty() && artist.isNotEmpty()) {
                val intent = Intent(ACTION_UPDATE_CURRENT_SONG)
                intent.putExtra(EXTRA_SONG_TITLE, title)
                intent.putExtra(EXTRA_SONG_ARTIST, artist)
                sendBroadcast(intent)
            }
        } catch (e: Exception) {
            Log.e("NLService", "Error parsing notification", e)
        }
    }
}
