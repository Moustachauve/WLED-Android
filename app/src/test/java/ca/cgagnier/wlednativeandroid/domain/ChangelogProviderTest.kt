package ca.cgagnier.wlednativeandroid.domain

import android.content.Context
import android.content.res.AssetManager
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.IOException

class ChangelogProviderTest {

    private val context: Context = mockk()
    private val assetManager: AssetManager = mockk()
    private lateinit var changelogProvider: ChangelogProvider

    @BeforeEach
    fun setUp() {
        every { context.assets } returns assetManager
        changelogProvider = ChangelogProvider(context).apply {
            isDebugBuild = false
        }
    }

    @Test
    fun `getChangelog lists and reads matching assets from AssetManager`() {
        every { assetManager.list("changelog") } returns arrayOf("7.0.1.md")
        every { assetManager.open("changelog/7.0.1.md") } returns ByteArrayInputStream(
            "Added new feature X".toByteArray(),
        )

        val result = changelogProvider.getChangelog("7.0.0", "7.0.1")

        assertEquals(
            listOf(VersionChangelog(version = "7.0.1", content = "Added new feature X")),
            result,
        )
    }

    @Test
    fun `getChangelog returns empty list when listing assets throws IOException`() {
        every { assetManager.list("changelog") } throws IOException("Cannot list assets")

        val result = changelogProvider.getChangelog("7.0.0", "7.0.1")

        assertTrue(result.isEmpty())
    }

    @Test
    fun `getChangelog skips asset when opening file throws IOException`() {
        every { assetManager.list("changelog") } returns arrayOf("7.0.1.md")
        every { assetManager.open("changelog/7.0.1.md") } throws IOException("Cannot read asset")

        val result = changelogProvider.getChangelog("7.0.0", "7.0.1")

        assertTrue(result.isEmpty())
    }
}
