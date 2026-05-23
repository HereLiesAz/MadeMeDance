package com.hereliesaz.mademedance.identify

import android.app.SearchManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.provider.Settings

sealed interface IdentifyResult {
    data class NowPlaying(val artist: String?, val title: String) : IdentifyResult
    data class OpenedApp(val label: String) : IdentifyResult
    data object NoRecognizer : IdentifyResult
}

/**
 * Free song identification, no API key. The chain:
 *  1. If media is playing on the device, read its title/artist directly.
 *  2. Otherwise launch the most preferred recognizer app installed (Google
 *     first), which listens live.
 *  3. If none is installed, open a web search and tell the user to install one.
 */
object SongIdentifier {

    // Ordered preference — Google's Sound Search first, then the usual suspects.
    private val recognizerPackages = listOf(
        "com.google.android.googlequicksearchbox", // Google app (Sound Search / Now Playing)
        "com.shazam.android",
        "com.melodis.midomiMusicIdentifier.freemium", // SoundHound (free)
        "com.melodis.midomiMusicIdentifier",          // SoundHound
        "com.musixmatch.android.lyrify"
    )

    fun identify(context: Context): IdentifyResult {
        readNowPlaying(context)?.let { return it }
        launchFirstRecognizer(context)?.let { return IdentifyResult.OpenedApp(it) }
        openWebSearch(context, "what song is this")
        return IdentifyResult.NoRecognizer
    }

    fun hasNotificationAccess(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver, "enabled_notification_listeners"
        ) ?: return false
        val me = context.packageName
        return enabled.split(":").any { it.contains(me) }
    }

    fun openNotificationAccessSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** Web search for an actually-known song — useful, unlike a blind "what is this song". */
    fun searchForKnownSong(context: Context, artist: String?, title: String) {
        val query = listOfNotNull(artist?.takeIf { it.isNotBlank() }, title).joinToString(" ")
        openWebSearch(context, query)
    }

    private fun readNowPlaying(context: Context): IdentifyResult.NowPlaying? {
        if (!hasNotificationAccess(context)) return null
        return try {
            val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
                ?: return null
            val component = ComponentName(context, NowPlayingListenerService::class.java)
            for (controller in msm.getActiveSessions(component)) {
                if (controller.playbackState?.state != PlaybackState.STATE_PLAYING) continue
                val metadata = controller.metadata ?: continue
                val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
                if (!title.isNullOrBlank()) {
                    val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
                        ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                    return IdentifyResult.NowPlaying(artist?.takeIf { it.isNotBlank() }, title)
                }
            }
            null
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun launchFirstRecognizer(context: Context): String? {
        val pm = context.packageManager
        for (pkg in recognizerPackages) {
            val launch = pm.getLaunchIntentForPackage(pkg)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ?: continue
            try {
                context.startActivity(launch)
                return appLabel(context, pkg)
            } catch (_: Exception) {
                // Try the next candidate.
            }
        }
        return null
    }

    private fun appLabel(context: Context, pkg: String): String = try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) {
        pkg
    }

    private fun openWebSearch(context: Context, query: String) {
        val search = Intent(Intent.ACTION_WEB_SEARCH).apply {
            putExtra(SearchManager.QUERY, query)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (search.resolveActivity(context.packageManager) != null) {
            context.startActivity(search)
        } else {
            val uri = Uri.parse("https://www.google.com/search?q=" + Uri.encode(query))
            context.startActivity(
                Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
