package ca.cgagnier.wlednativeandroid.domain

import android.content.Context
import android.content.res.AssetManager
import com.diffplug.selfie.Selfie.expectSelfie
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
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
        val result = changelogProvider.getChangelog("7.0.0", "not-a-version")
        assertNull(result)
    }

    @Test
    fun `getChangelog returns null when last seen equals current version`() {
        every { assetManager.list("changelog") } returns arrayOf("7.0.1.md")

        val result = changelogProvider.getChangelog("7.0.1", "7.0.1")
        assertNull(result)
    }

    @Test
    fun `getChangelog returns relevant versions newer than last seen`() {
        every { assetManager.list("changelog") } returns arrayOf(
            "7.0.0.md",
            "7.0.1.md",
            "7.0.2.md",
            "7.1.0.md",
        )
        every { assetManager.open("changelog/7.0.1.md") } returns ByteArrayInputStream(
            "Added new feature X".toByteArray(),
        )
        every { assetManager.open("changelog/7.0.2.md") } returns ByteArrayInputStream(
            "Fixed bug Y".toByteArray(),
        )

        val result = changelogProvider.getChangelog("7.0.0", "7.0.2")

        assertNotNull(result)
        expectSelfie(result!!).toMatchDisk()
    }

    @Test
    fun `getChangelog includes dev md for beta releases`() {
        every { assetManager.list("changelog") } returns arrayOf(
            "7.0.1.md",
            "dev.md",
        )
        every { assetManager.open("changelog/dev.md") } returns ByteArrayInputStream(
            "Bleeding edge updates".toByteArray(),
        )
        every { assetManager.open("changelog/7.0.1.md") } returns ByteArrayInputStream(
            "Stable release 7.0.1".toByteArray(),
        )

        val result = changelogProvider.getChangelog("7.0.0", "7.1.0-beta1")

        assertNotNull(result)
        expectSelfie(result!!).toMatchDisk()
    }

    @Test
    fun `getChangelog ignores non-version files such as README md`() {
        every { assetManager.list("changelog") } returns arrayOf(
            "README.md",
            "7.0.1.md",
        )
        every { assetManager.open("changelog/7.0.1.md") } returns ByteArrayInputStream(
            "Added new feature X".toByteArray(),
        )

        val result = changelogProvider.getChangelog("7.0.0", "7.0.1")

        assertNotNull(result)
        expectSelfie(result!!).toMatchDisk()
    }
}
