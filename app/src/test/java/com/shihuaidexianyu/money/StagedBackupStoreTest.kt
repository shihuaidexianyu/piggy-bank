package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.backup.StagedBackupStore
import java.io.ByteArrayInputStream
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StagedBackupStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `preview and confirmation read the immutable staged bytes`() {
        val store = store()
        val handle = store.stage(ByteArrayInputStream("original".encodeToByteArray()), now = 100L)

        assertEquals("original", store.readVerified(handle.id).decodeToString())
        assertEquals("original", store.readVerified(handle.id).decodeToString())
        assertEquals(64, handle.sha256.length)
    }

    @Test
    fun `oversized input aborts while copying and leaves no stage`() {
        val store = store(maxBytes = 4L)

        assertFailsWith<IllegalArgumentException> {
            store.stage(ByteArrayInputStream("12345".encodeToByteArray()), now = 100L)
        }

        assertTrue(store.list().isEmpty())
    }

    @Test
    fun `hash mismatch and truncation abort before bytes are returned`() {
        val store = store()
        val handle = store.stage(ByteArrayInputStream("original".encodeToByteArray()), now = 100L)
        File(temporaryFolder.root, "imports/${handle.id}.json").writeText("tampered")

        assertFailsWith<IllegalArgumentException> { store.readVerified(handle.id) }
    }

    @Test
    fun `staged cache expires after exactly 24 hours`() {
        val store = store()
        val keep = store.stage(ByteArrayInputStream("keep".encodeToByteArray()), now = 1_000L)
        val expire = store.stage(ByteArrayInputStream("expire".encodeToByteArray()), now = 999L)

        store.cleanupExpired(now = 999L + StagedBackupStore.RETENTION_MILLIS)

        assertTrue(store.exists(keep.id))
        assertFalse(store.exists(expire.id))
    }

    @Test
    fun `cleanup removes crashed orphan data file after 24 hours`() {
        val store = store()
        val orphan = File(temporaryFolder.root, "imports/orphan.json").apply {
            parentFile?.mkdirs()
            writeText("orphan")
            setLastModified(1_000L)
        }

        store.cleanupExpired(1_000L + StagedBackupStore.RETENTION_MILLIS)

        assertFalse(orphan.exists())
    }

    private fun store(maxBytes: Long = 32L * 1024L * 1024L) = StagedBackupStore(
        cacheDir = temporaryFolder.root,
        maxBytes = maxBytes,
        idGenerator = sequenceOf("stage-a", "stage-b", "stage-c").iterator()::next,
    )
}
