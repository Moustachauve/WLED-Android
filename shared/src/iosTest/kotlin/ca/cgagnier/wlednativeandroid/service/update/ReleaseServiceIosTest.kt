package ca.cgagnier.wlednativeandroid.service.update

import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.model.Repository
import ca.cgagnier.wlednativeandroid.model.githubapi.Author
import ca.cgagnier.wlednativeandroid.model.githubapi.Release
import ca.cgagnier.wlednativeandroid.repository.DevicesDatabase
import ca.cgagnier.wlednativeandroid.repository.createDeviceRepository
import ca.cgagnier.wlednativeandroid.repository.createInMemoryDatabase
import ca.cgagnier.wlednativeandroid.repository.createVersionWithAssetsRepository
import ca.cgagnier.wlednativeandroid.service.api.DownloadState
import ca.cgagnier.wlednativeandroid.service.api.github.GithubApi
import ca.cgagnier.wlednativeandroid.service.api.github.GithubApiEndpoints
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okio.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ReleaseServiceIosTest {
    private val database = DevicesDatabase.createInMemoryDatabase()
    private val deviceRepository = database.createDeviceRepository()
    private val repositoryDao = database.repositoryDao()
    private val versionWithAssetsRepository = database.createVersionWithAssetsRepository()

    @AfterTest
    fun tearDown() {
        database.close()
    }

    private fun makeRelease(tagName: String): Release = Release(
        url = "https://api.github.com/repos/wled/WLED/releases/1",
        assetsUrl = "https://api.github.com/repos/wled/WLED/releases/1/assets",
        uploadUrl = "https://uploads.github.com/repos/wled/WLED/releases/1/assets",
        htmlUrl = "https://github.com/wled/WLED/releases/tag/$tagName",
        id = 1,
        author = Author(
            login = "wled",
            id = 1,
            nodeId = "MDQ6VXNlcjE=",
            avatarUrl = "",
            gravatarId = "",
            url = "",
            htmlUrl = "",
            followersUrl = "",
            followingUrl = "",
            gistsUrl = "",
            starredUrl = "",
            subscriptionsUrl = "",
            organizationsUrl = "",
            reposUrl = "",
            eventsUrl = "",
            receivedEventsUrl = "",
            type = "User",
            siteAdmin = false,
        ),
        nodeId = "RE_1",
        tagName = tagName,
        targetCommitish = "main",
        name = tagName,
        draft = false,
        prerelease = false,
        createdAt = "2025-01-01T00:00:00Z",
        publishedAt = "2025-01-01T00:00:00Z",
        assets = emptyList(),
        tarballUrl = "",
        zipballUrl = "",
        body = "Release notes for $tagName",
    )

    private fun fakeGithubApi(onGetReleases: (String, String) -> List<Release>): GithubApi = GithubApi(
        object : GithubApiEndpoints {
            override suspend fun getAllReleases(repoOwner: String, repoName: String): List<Release> =
                onGetReleases(repoOwner, repoName)

            override fun downloadReleaseBinary(
                repoOwner: String,
                repoName: String,
                assetId: Int,
                destinationPath: Path,
            ): Flow<DownloadState> = emptyFlow()
        },
    )

    @Test
    fun syncBuiltInRepositoriesSeedsDefaultRepositories() = runTest {
        val service = ReleaseService(
            versionWithAssetsRepository,
            repositoryDao,
            StandardTestDispatcher(testScheduler),
        )

        service.syncBuiltInRepositories()

        val savedRepos = repositoryDao.getAllRepositories().first().map { it.ownerAndRepo }.toSet()
        assertEquals(Repository.BUILT_IN_REPOSITORIES.map { it.ownerAndRepo }.toSet(), savedRepos)
    }

    @Test
    fun refreshVersionsForUsedRepositoriesReturnsFalseWhenNoDevicesExist() = runTest {
        val service = ReleaseService(
            versionWithAssetsRepository,
            repositoryDao,
            StandardTestDispatcher(testScheduler),
        )
        service.syncBuiltInRepositories()

        val succeeded = service.refreshVersionsForUsedRepositories(
            fakeGithubApi { _, _ -> listOf(makeRelease("v0.15.0")) },
            deviceRepository,
        )

        assertFalse(succeeded)
    }

    @Test
    fun refreshVersionsForUsedRepositoriesPersistsReleasesForUsedRepositories() = runTest {
        val service = ReleaseService(
            versionWithAssetsRepository,
            repositoryDao,
            StandardTestDispatcher(testScheduler),
        )
        service.syncBuiltInRepositories()
        deviceRepository.insert(
            Device(
                macAddress = "AABBCCDDEEFF",
                address = "192.168.1.50",
                repositoryId = Repository.DEFAULT_ID,
            ),
        )

        val succeeded = service.refreshVersionsForUsedRepositories(
            fakeGithubApi { _, _ -> listOf(makeRelease("v0.15.0")) },
            deviceRepository,
        )

        assertTrue(succeeded)
        val savedVersion = versionWithAssetsRepository.getLatestStableVersionWithAssets(Repository.DEFAULT_ID)
        assertNotNull(savedVersion)
        assertEquals("0.15.0", savedVersion.version.tagName)
    }
}
