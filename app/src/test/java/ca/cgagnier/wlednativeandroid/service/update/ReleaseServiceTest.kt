package ca.cgagnier.wlednativeandroid.service.update

import ca.cgagnier.wlednativeandroid.model.Branch
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.model.Repository
import ca.cgagnier.wlednativeandroid.model.Version
import ca.cgagnier.wlednativeandroid.model.VersionWithAssets
import ca.cgagnier.wlednativeandroid.model.githubapi.Release
import ca.cgagnier.wlednativeandroid.model.wledapi.Info
import ca.cgagnier.wlednativeandroid.model.wledapi.Leds
import ca.cgagnier.wlednativeandroid.model.wledapi.Wifi
import ca.cgagnier.wlednativeandroid.repository.DeviceRepository
import ca.cgagnier.wlednativeandroid.repository.RepositoryDao
import ca.cgagnier.wlednativeandroid.repository.VersionWithAssetsRepository
import ca.cgagnier.wlednativeandroid.service.api.github.GithubApi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReleaseServiceTest {

    private val versionWithAssetsRepository: VersionWithAssetsRepository = mockk()
    private val repositoryDao: RepositoryDao = mockk()
    private val githubApi: GithubApi = mockk()
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
        releaseService = ReleaseService(
            versionWithAssetsRepository,
            repositoryDao,
            UnconfinedTestDispatcher(),
        )
        coEvery { repositoryDao.getRepositoryByOwnerAndRepo("wled/WLED") } returns defaultRepo
    }

    @Test
    fun `getNewerReleaseTag offers newer stable release`() = runTest {
        val deviceInfo = createInfo(version = "0.15.2")
        coEvery {
            versionWithAssetsRepository.getLatestStableVersionWithAssets(1L)
        } returns createVersionWithAssets("0.15.3")

        val result = releaseService.getNewerReleaseTag(deviceInfo, Branch.STABLE, ignoreVersion = "")

        assertEquals("0.15.3", result)
    }

    @Test
    fun `getNewerReleaseTag returns null when device is already up to date`() = runTest {
        val deviceInfo = createInfo(version = "0.15.3")
        coEvery {
            versionWithAssetsRepository.getLatestStableVersionWithAssets(1L)
        } returns createVersionWithAssets("0.15.3")

        val result = releaseService.getNewerReleaseTag(deviceInfo, Branch.STABLE, ignoreVersion = "")

        assertNull(result)
    }

    @Test
    fun `getNewerReleaseTag returns null when newer release matches ignored version`() = runTest {
        val deviceInfo = createInfo(version = "0.15.2")
        coEvery {
            versionWithAssetsRepository.getLatestStableVersionWithAssets(1L)
        } returns createVersionWithAssets("0.15.3")

        val result = releaseService.getNewerReleaseTag(deviceInfo, Branch.STABLE, ignoreVersion = "0.15.3")

        assertNull(result)
    }

    @Test
    fun `getNewerReleaseTag returns null when OTA is disabled`() = runTest {
        val deviceInfo = createInfo(version = "0.15.2", options = 0x00) // OTA disabled
        coEvery {
            versionWithAssetsRepository.getLatestStableVersionWithAssets(1L)
        } returns createVersionWithAssets("0.15.3")

        val result = releaseService.getNewerReleaseTag(deviceInfo, Branch.STABLE, ignoreVersion = "")

        assertNull(result)
    }

    @Test
    fun `getNewerReleaseTag offers transition from beta to stable branch`() = runTest {
        val deviceInfo = createInfo(version = "0.16.0-b1")
        coEvery {
            versionWithAssetsRepository.getLatestStableVersionWithAssets(1L)
        } returns createVersionWithAssets("0.15.3")

        val result = releaseService.getNewerReleaseTag(deviceInfo, Branch.STABLE, ignoreVersion = "")

        assertEquals("0.15.3", result)
    }

    @Test
    fun `getNewerReleaseTag offers newer beta release`() = runTest {
        val deviceInfo = createInfo(version = "0.16.0-b1")
        coEvery {
            versionWithAssetsRepository.getLatestBetaVersionWithAssets(1L)
        } returns createVersionWithAssets("0.16.0-b2")

        val result = releaseService.getNewerReleaseTag(deviceInfo, Branch.BETA, ignoreVersion = "")

        assertEquals("0.16.0-b2", result)
    }

    @Test
    fun `getNewerReleaseTag returns null when device has a newer version than repo`() = runTest {
        val deviceInfo = createInfo(version = "0.16.0")
        coEvery {
            versionWithAssetsRepository.getLatestStableVersionWithAssets(1L)
        } returns createVersionWithAssets("0.15.3")

        val result = releaseService.getNewerReleaseTag(deviceInfo, Branch.STABLE, ignoreVersion = "")

        assertNull(result)
    }

    @Test
    fun `getNewerReleaseTag offers update when latest release has non-semver tag`() = runTest {
        val deviceInfo = createInfo(version = "0.15.2")
        coEvery {
            versionWithAssetsRepository.getLatestStableVersionWithAssets(1L)
        } returns createVersionWithAssets("custom-build-2026")

        val result = releaseService.getNewerReleaseTag(deviceInfo, Branch.STABLE, ignoreVersion = "")

        assertEquals("custom-build-2026", result)
    }

    @Test
    fun `getNewerReleaseTag returns null when deviceInfo version is null`() = runTest {
        val deviceInfo = createInfo(version = null)

        val result = releaseService.getNewerReleaseTag(deviceInfo, Branch.STABLE, ignoreVersion = "")

        assertNull(result)
    }

    @Test
    fun `syncBuiltInRepositories inserts missing built-in repositories`() = runTest {
        coEvery { repositoryDao.getRepositoryByOwnerAndRepo(any()) } returns null
        coEvery { repositoryDao.insert(any()) } returns 1L
        every { repositoryDao.getAllRepositories() } returns flowOf(emptyList())

        releaseService.syncBuiltInRepositories()

        Repository.BUILT_IN_REPOSITORIES.forEach { builtin ->
            coVerify { repositoryDao.insert(builtin) }
        }
    }

    @Test
    fun `syncBuiltInRepositories clears default flag on non-built-in repositories`() = runTest {
        val staleCustomRepo = Repository(
            id = 99L,
            name = "OldDefault",
            ownerAndRepo = "custom/WLED",
            description = "Custom fork",
            htmlUrl = "https://github.com/custom/WLED",
            isDefault = true,
        )
        coEvery { repositoryDao.getRepositoryByOwnerAndRepo(any()) } returns defaultRepo
        coEvery { repositoryDao.update(any()) } returns Unit
        every { repositoryDao.getAllRepositories() } returns flowOf(listOf(staleCustomRepo))

        releaseService.syncBuiltInRepositories()

        coVerify { repositoryDao.update(staleCustomRepo.copy(isDefault = false)) }
    }

    @Test
    fun `refreshVersions returns false when a repository fails and still updates successful ones`() = runTest {
        val release = mockk<Release>(relaxed = true) {
            every { tagName } returns "v0.15.0"
            every { name } returns "0.15.0"
            every { body } returns "Notes"
            every { prerelease } returns false
            every { publishedAt } returns "2025-01-01T00:00:00Z"
            every { htmlUrl } returns "https://github.com/wled/WLED/releases/tag/v0.15.0"
            every { assets } returns emptyList()
        }
        coEvery { githubApi.getAllReleases("wled", "WLED") } returns Result.success(listOf(release))
        coEvery { githubApi.getAllReleases("broken", "fork") } returns Result.failure(Exception("Network error"))
        coEvery { versionWithAssetsRepository.updateRepository(any(), any()) } returns Unit

        val succeeded = releaseService.refreshVersions(githubApi, setOf("wled/WLED", "broken/fork"))

        assertFalse(succeeded)
        coVerify(exactly = 1) {
            versionWithAssetsRepository.updateRepository(
                match { it.ownerAndRepo == "wled/WLED" },
                any(),
            )
        }
    }

    @Test
    fun `refreshVersionsForUsedRepositories only refreshes enabled and used repositories`() = runTest {
        val deviceRepository: DeviceRepository = mockk()
        val usedEnabledRepo = defaultRepo.copy(id = 1L, isEnabled = true, isUpdateEnabled = true)
        val unusedRepo = defaultRepo.copy(id = 2L, ownerAndRepo = "unused/WLED", isEnabled = true)
        val disabledRepo = defaultRepo.copy(id = 3L, ownerAndRepo = "disabled/WLED", isEnabled = false)

        coEvery { deviceRepository.getUsedRepositoryIds() } returns listOf(1L, 3L)
        every { repositoryDao.getAllRepositories() } returns flowOf(
            listOf(usedEnabledRepo, unusedRepo, disabledRepo),
        )
        coEvery { githubApi.getAllReleases("wled", "WLED") } returns Result.success(emptyList())

        val succeeded = releaseService.refreshVersionsForUsedRepositories(githubApi, deviceRepository)

        assertTrue(succeeded)
        coVerify(exactly = 1) { githubApi.getAllReleases("wled", "WLED") }
        coVerify(exactly = 0) { githubApi.getAllReleases("unused", "WLED") }
        coVerify(exactly = 0) { githubApi.getAllReleases("disabled", "WLED") }
    }

    @Test
    fun `refreshVersionsForUsedRepositories returns false when no repositories are used`() = runTest {
        val deviceRepository: DeviceRepository = mockk()
        coEvery { deviceRepository.getUsedRepositoryIds() } returns emptyList()
        every { repositoryDao.getAllRepositories() } returns flowOf(listOf(defaultRepo))

        val succeeded = releaseService.refreshVersionsForUsedRepositories(githubApi, deviceRepository)

        assertFalse(succeeded)
        coVerify(exactly = 0) { githubApi.getAllReleases(any(), any()) }
    }

    @Test
    fun `refreshVersionsForDevice refreshes device repository`() = runTest {
        val customRepo = defaultRepo.copy(id = 5L, ownerAndRepo = "MoonModules/WLED")
        val device = Device(macAddress = "AABBCCDDEEFF", address = "192.168.1.50", repositoryId = 5L)

        coEvery { repositoryDao.getRepositoryById(5L) } returns customRepo
        coEvery { githubApi.getAllReleases("MoonModules", "WLED") } returns Result.success(emptyList())

        val succeeded = releaseService.refreshVersionsForDevice(githubApi, device)

        assertTrue(succeeded)
        coVerify(exactly = 1) { githubApi.getAllReleases("MoonModules", "WLED") }
    }

    private fun createInfo(version: String?, options: Int = 0x01): Info = Info(
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
            publishedDate = "2026-01-15T00:00:00Z",
            htmlUrl = "https://github.com/wled/WLED/releases/tag/$tagName",
        ),
        assets = emptyList(),
    )
}
