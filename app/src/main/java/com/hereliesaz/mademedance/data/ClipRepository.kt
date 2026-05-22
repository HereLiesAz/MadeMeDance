package com.hereliesaz.mademedance.data

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ClipItem(
    val name: String,
    val file: File,
    val timestamp: Long
) {
    val formattedDate: String
        get() = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault())
            .format(Date(timestamp))
}

class ClipRepository(private val storageDir: File?) {

    fun getClips(): List<ClipItem> {
        val dir = storageDir ?: return emptyList()
        return dir.listFiles { file -> file.name.endsWith(".mmd") }
            ?.map { file ->
                val timestamp = extractTimestamp(file.name)
                ClipItem(name = file.name, file = file, timestamp = timestamp)
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
        return file.delete()
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
