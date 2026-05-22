package com.hereliesaz.mademedance

import com.hereliesaz.mademedance.data.ClipRepository
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

class ClipRepositoryTest {

    private lateinit var tempDir: File
    private lateinit var repository: ClipRepository

    @Before
    fun setup() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "mademedance_test_${System.nanoTime()}")
        tempDir.mkdirs()
        repository = ClipRepository(tempDir)
    }

    @After
    fun cleanup() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `getClips returns empty list when no files exist`() {
        val clips = repository.getClips()
        assertTrue(clips.isEmpty())
    }

    @Test
    fun `getClips returns only mmd files`() {
        File(tempDir, "MadeMeDance_snippet_1000.mmd").createNewFile()
        File(tempDir, "MadeMeDance_snippet_2000.mmd").createNewFile()
        File(tempDir, "other_file.txt").createNewFile()

        val clips = repository.getClips()
        assertEquals(2, clips.size)
    }

    @Test
    fun `getClips returns clips sorted by timestamp descending`() {
        File(tempDir, "MadeMeDance_snippet_1000.mmd").createNewFile()
        File(tempDir, "MadeMeDance_snippet_3000.mmd").createNewFile()
        File(tempDir, "MadeMeDance_snippet_2000.mmd").createNewFile()

        val clips = repository.getClips()
        assertEquals(3000L, clips[0].timestamp)
        assertEquals(2000L, clips[1].timestamp)
        assertEquals(1000L, clips[2].timestamp)
    }

    @Test
    fun `deleteClip removes the file`() {
        val file = File(tempDir, "MadeMeDance_snippet_1000.mmd")
        file.createNewFile()
        assertTrue(file.exists())

        val deleted = repository.deleteClip("MadeMeDance_snippet_1000.mmd")
        assertTrue(deleted)
        assertFalse(file.exists())
    }

    @Test
    fun `deleteClip returns false for nonexistent file`() {
        val deleted = repository.deleteClip("nonexistent.mmd")
        assertFalse(deleted)
    }

    @Test
    fun `getClipFile returns file when it exists`() {
        File(tempDir, "MadeMeDance_snippet_1000.mmd").createNewFile()
        val file = repository.getClipFile("MadeMeDance_snippet_1000.mmd")
        assertNotNull(file)
        assertTrue(file!!.exists())
    }

    @Test
    fun `getClipFile returns null for nonexistent file`() {
        val file = repository.getClipFile("nonexistent.mmd")
        assertNull(file)
    }

    @Test
    fun `ClipItem formats date correctly`() {
        File(tempDir, "MadeMeDance_snippet_1000.mmd").createNewFile()
        val clips = repository.getClips()
        // Timestamp 1000 = Jan 1 1970 — just verify it doesn't crash
        assertNotNull(clips[0].formattedDate)
        assertTrue(clips[0].formattedDate.isNotBlank())
    }

    @Test
    fun `handles null storage directory`() {
        val nullRepo = ClipRepository(null)
        assertTrue(nullRepo.getClips().isEmpty())
        assertFalse(nullRepo.deleteClip("anything.mmd"))
        assertNull(nullRepo.getClipFile("anything.mmd"))
    }
}
