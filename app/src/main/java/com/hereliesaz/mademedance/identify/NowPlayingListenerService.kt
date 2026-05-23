package com.hereliesaz.mademedance.identify

import android.service.notification.NotificationListenerService

/**
 * Exists only so the user can grant notification access, which is what unlocks
 * [android.media.session.MediaSessionManager.getActiveSessions] for reading the
 * device's currently-playing media. We don't process notifications here.
 */
class NowPlayingListenerService : NotificationListenerService()
