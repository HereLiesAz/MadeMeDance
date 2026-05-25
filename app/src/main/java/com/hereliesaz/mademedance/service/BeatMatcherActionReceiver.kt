package com.hereliesaz.mademedance.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BeatMatcherActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_STOP = "com.hereliesaz.mademedance.action.NOTIFICATION_STOP"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == ACTION_STOP) {
            BeatMatcherService.stop(context)
        }
    }
}
