package ca.cgagnier.wlednativeandroid.domain

import android.content.Context
import android.content.res.AssetManager
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

class ChangelogProviderTest {

    private val context: Context = mockk()
    private val assetManager: AssetManager = mockk()
    private lateinit var changelogProvider: ChangelogProvider

    @BeforeEach
    fun setUp() {
        every { context.assets } returns assetManager
        changelogProvider = ChangelogProvider(context)
    }

    @Test
    fun `getChangelog returns null when current version is invalid`() {
        val result = changelogProvider.getChangelog("0.13.0", "not-a-version")
        assertNull(result)
    }

    @Test
    fun `getChangelog returns null when last seen equals current version`() {
        every { assetManager.list("changelog") } returns arrayOf("0.14.0.md")

        val result = changelogProvider.getChangelog("0.14.0", "0.14.0")
        assertNull(result)
    }

    @Test
    fun `getChangelog returns relevant versions newer than last seen`() {
        every { assetManager.list("changelog") } returns arrayOf(
            "0.13.0.md",
            "0.14.0.md",
            "0.14.1.md",
            "0.15.0.md",
        )
        every { assetManager.open("changelog/0.14.0.md") } returns ByteArrayInputStream(
            "Added new feature X".toByteArray(),
        )
        every { assetManager.open("changelog/0.14.1.md") } returns ByteArrayInputStream(
            "Fixed bug Y".toByteArray(),
        )

        val result = changelogProvider.getChangelog("0.13.0", "0.14.1")

        assertNotNull(result)
        assertTrue(result!!.contains("Version 0.14.1"))
        assertTrue(result.contains("Fixed bug Y"))
        assertTrue(result.contains("Version 0.14.0"))
        assertTrue(result.contains("Added new feature X"))
    }

    @Test
    fun `getChangelog includes dev md for beta releases`() {
        every { assetManager.list("changelog") } returns arrayOf(
            "0.14.0.md",
            "dev.md",
        )
        every { assetManager.open("changelog/dev.md") } returns ByteArrayInputStream(
            "Bleeding edge updates".toByteArray(),
        )
        every { assetManager.open("changelog/0.14.0.md") } returns ByteArrayInputStream(
            "Stable release 0.14".toByteArray(),
        )

        val result = changelogProvider.getChangelog("0.13.0", "0.15.0-beta1")

        assertNotNull(result)
        assertTrue(result!!.contains("Version Dev"))
        assertTrue(result.contains("Bleeding edge updates"))
    }
}
