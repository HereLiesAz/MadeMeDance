package com.hereliesaz.mademedance.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.hereliesaz.mademedance.identify.IdentifyResult
import com.hereliesaz.mademedance.identify.SongIdentifier

/**
 * Runs the identification chain. For the in-app "now playing" result it calls
 * [onNowPlaying] so the caller can show a dialog; the other outcomes
 * (launching an app, or nothing installed) are surfaced with a toast since the
 * chain has already navigated away.
 */
fun runIdentify(context: Context, onNowPlaying: (IdentifyResult.NowPlaying) -> Unit) {
    when (val result = SongIdentifier.identify(context)) {
        is IdentifyResult.NowPlaying -> onNowPlaying(result)
        is IdentifyResult.OpenedApp ->
            Toast.makeText(context, "Opening ${result.label}…", Toast.LENGTH_SHORT).show()
        IdentifyResult.NoRecognizer ->
            Toast.makeText(
                context,
                "No song finder installed — grab Shazam, SoundHound, or the Google app.",
                Toast.LENGTH_LONG
            ).show()
    }
}

@Composable
fun NowPlayingDialog(
    nowPlaying: IdentifyResult.NowPlaying,
    onSearch: () -> Unit,
    onDismiss: () -> Unit
) {
    val label = listOfNotNull(nowPlaying.artist, nowPlaying.title).joinToString(" — ")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Playing now") },
        text = { Text(label) },
        confirmButton = {
            TextButton(onClick = onSearch) { Text("Search the web") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
