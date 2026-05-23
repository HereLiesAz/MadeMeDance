package com.hereliesaz.mademedance.data

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ClipItem(
    val name: String,
    val file: File,
    val timestamp: Long,
    val artist: String? = null,
    val title: String? = null
) {
    val knownSong: Boolean get() = !title.isNullOrBlank()

    val formattedDate: String
        get() = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault())
            .format(Date(timestamp))
}

/** Sidecar that records an identified song next to its clip. */
private fun metaFileFor(clipFile: File): File = File(clipFile.parentFile, clipFile.name + ".meta")

/** Write a clip's identified song. Usable without a [ClipRepository] instance (e.g. the service). */
fun writeClipMeta(clipFile: File, title: String, artist: String?) {
    try {
        metaFileFor(clipFile).writeText("$title\n${artist ?: ""}")
    } catch (_: Exception) { /* best-effort */ }
}

class ClipRepository(private val storageDir: File?) {

    fun getClips(): List<ClipItem> {
        val dir = storageDir ?: return emptyList()
        return dir.listFiles { file -> file.name.endsWith(".mmd") }
            ?.map { file ->
                val (title, artist) = readMeta(file)
                ClipItem(
                    name = file.name,
                    file = file,
                    timestamp = extractTimestamp(file.name),
                    artist = artist,
                    title = title
                )
            }
            ?.sortedByDescending { it.timestamp }
            ?: emptyList()
    }

    fun getClipFile(clipName: String): File? {
        val dir = storageDir ?: return null
        val file = File(dir, clipName)
        return if (file.exists()) file else null
    }

    fun deleteClip(clipName: String): Boolean {
        val file = getClipFile(clipName) ?: return false
        metaFileFor(file).delete() // best-effort; ignore result
        return file.delete()
    }

    /** Returns (title, artist); either may be null when no sidecar exists. */
    fun readMeta(clipFile: File): Pair<String?, String?> {
        return try {
            val meta = metaFileFor(clipFile)
            if (!meta.exists()) return null to null
            val lines = meta.readLines()
            val title = lines.getOrNull(0)?.takeIf { it.isNotBlank() }
            val artist = lines.getOrNull(1)?.takeIf { it.isNotBlank() }
            title to artist
        } catch (_: Exception) {
            null to null
        }
    }

    fun writeMeta(clipName: String, title: String, artist: String?) {
        val file = getClipFile(clipName) ?: return
        writeClipMeta(file, title, artist)
    }

    private fun extractTimestamp(fileName: String): Long {
        // Format: MadeMeDance_snippet_<timestamp>.mmd
        return try {
            fileName.removePrefix("MadeMeDance_snippet_")
                .removeSuffix(".mmd")
                .toLong()
        } catch (e: NumberFormatException) {
            0L
        }
    }
}
