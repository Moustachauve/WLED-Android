package ca.cgagnier.wlednativeandroid.service.update

import ca.cgagnier.wlednativeandroid.model.Branch
import ca.cgagnier.wlednativeandroid.model.Repository
import ca.cgagnier.wlednativeandroid.model.Version
import ca.cgagnier.wlednativeandroid.model.VersionWithAssets
import ca.cgagnier.wlednativeandroid.model.wledapi.Info
import ca.cgagnier.wlednativeandroid.model.wledapi.Leds
import ca.cgagnier.wlednativeandroid.model.wledapi.Wifi
import ca.cgagnier.wlednativeandroid.repository.RepositoryDao
import ca.cgagnier.wlednativeandroid.repository.VersionWithAssetsRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ReleaseServiceTest {

    private val versionWithAssetsRepository: VersionWithAssetsRepository = mockk()
    private val repositoryDao: RepositoryDao = mockk()
    private lateinit var releaseService: ReleaseService

    private val defaultRepo = Repository(
        id = 1L,
        name = "WLED",
        ownerAndRepo = "wled/WLED",
        description = "Official WLED",
        htmlUrl = "https://github.com/wled/WLED",
    )

    @BeforeEach
    fun setUp() {
        releaseService = ReleaseService(versionWithAssetsRepository, repositoryDao)
        coEvery { repositoryDao.getRepositoryByOwnerAndRepo("wled/WLED") } returns defaultRepo
    }

    @Test
    fun `getNewerReleaseTag offers newer stable release`() = runTest {
        val deviceInfo = createInfo(version = "0.14.0")
        coEvery {
            versionWithAssetsRepository.getLatestStableVersionWithAssets(1L)
        } returns createVersionWithAssets("0.14.1")

        val result = releaseService.getNewerReleaseTag(deviceInfo, Branch.STABLE, ignoreVersion = "")

        assertEquals("0.14.1", result)
    }

    @Test
    fun `getNewerReleaseTag returns null when device is already up to date`() = runTest {
        val deviceInfo = createInfo(version = "0.14.1")
        coEvery {
            versionWithAssetsRepository.getLatestStableVersionWithAssets(1L)
        } returns createVersionWithAssets("0.14.1")

        val result = releaseService.getNewerReleaseTag(deviceInfo, Branch.STABLE, ignoreVersion = "")

        assertNull(result)
    }

    @Test
    fun `getNewerReleaseTag returns null when newer release matches ignored version`() = runTest {
        val deviceInfo = createInfo(version = "0.14.0")
        coEvery {
            versionWithAssetsRepository.getLatestStableVersionWithAssets(1L)
        } returns createVersionWithAssets("0.14.1")

        val result = releaseService.getNewerReleaseTag(deviceInfo, Branch.STABLE, ignoreVersion = "0.14.1")

        assertNull(result)
    }

    @Test
    fun `getNewerReleaseTag returns null when OTA is disabled`() = runTest {
        val deviceInfo = createInfo(version = "0.14.0", options = 0x00) // OTA disabled
        coEvery {
            versionWithAssetsRepository.getLatestStableVersionWithAssets(1L)
        } returns createVersionWithAssets("0.14.1")

        val result = releaseService.getNewerReleaseTag(deviceInfo, Branch.STABLE, ignoreVersion = "")

        assertNull(result)
    }

    @Test
    fun `getNewerReleaseTag offers transition from beta to stable branch`() = runTest {
        val deviceInfo = createInfo(version = "0.15.0-b1")
        coEvery {
            versionWithAssetsRepository.getLatestStableVersionWithAssets(1L)
        } returns createVersionWithAssets("0.14.4")

        val result = releaseService.getNewerReleaseTag(deviceInfo, Branch.STABLE, ignoreVersion = "")

        assertEquals("0.14.4", result)
    }

    @Test
    fun `getNewerReleaseTag offers newer beta release`() = runTest {
        val deviceInfo = createInfo(version = "0.15.0-b1")
        coEvery {
            versionWithAssetsRepository.getLatestBetaVersionWithAssets(1L)
        } returns createVersionWithAssets("0.15.0-b2")

        val result = releaseService.getNewerReleaseTag(deviceInfo, Branch.BETA, ignoreVersion = "")

        assertEquals("0.15.0-b2", result)
    }

    private fun createInfo(version: String, options: Int = 0x01): Info = Info(
        leds = Leds(count = 30, fps = 30, maxPower = 0, maxSegment = 1),
        wifi = Wifi(bssid = "mac", rssi = -50, signal = 100, channel = 1),
        name = "Test Device",
        version = version,
        options = options,
        brand = "WLED",
    )

    private fun createVersionWithAssets(tagName: String): VersionWithAssets = VersionWithAssets(
        version = Version(
            id = 10L,
            repositoryId = 1L,
            tagName = tagName,
            name = "Release $tagName",
            description = "Notes",
            isPrerelease = tagName.contains("-"),
            publishedDate = "2024-01-01T00:00:00Z",
            htmlUrl = "https://github.com/wled/WLED/releases/tag/$tagName",
        ),
        assets = emptyList(),
    )
}
