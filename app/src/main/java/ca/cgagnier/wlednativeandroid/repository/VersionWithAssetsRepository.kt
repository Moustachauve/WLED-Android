package ca.cgagnier.wlednativeandroid.repository

import androidx.annotation.WorkerThread
import androidx.room.withTransaction
import ca.cgagnier.wlednativeandroid.model.Asset
import ca.cgagnier.wlednativeandroid.model.Repository
import ca.cgagnier.wlednativeandroid.model.Version
import ca.cgagnier.wlednativeandroid.model.VersionWithAssets
import javax.inject.Inject

private const val IGNORED_TAG = "nightly"

class VersionWithAssetsRepository @Inject constructor(
    private val database: DevicesDatabase,
    private val repositoryDao: RepositoryDao,
    private val versionDao: VersionDao,
    private val assetDao: AssetDao,
) {

    @WorkerThread
    suspend fun updateRepository(repository: Repository, versionToAssetsMap: Map<Version, List<Asset>>) {
        database.withTransaction {
            // Ensure repository exists and get its ID
            val existingRepo = repositoryDao.getRepositoryByOwnerAndRepo(repository.ownerAndRepo)
            val repoToInsert = if (repository.ownerAndRepo.equals(Repository.DEFAULT_OWNER_REPO, ignoreCase = true)) {
                repository.copy(id = Repository.DEFAULT_ID)
            } else {
                repository
            }

            val repoId = if (existingRepo != null) {
                repositoryDao.update(repoToInsert.copy(id = existingRepo.id))
                existingRepo.id
            } else {
                repositoryDao.insert(repoToInsert)
            }

            // Get existing data for this repository
            val existingVersions = versionDao.getVersionsByRepository(repoId)
            val existingAssets = assetDao.getAssetsByRepository(repoId)

            // Find versions that were removed from GitHub
            val newVersionTags = versionToAssetsMap.keys.map { it.tagName }.toSet()
            val versionsToDelete = existingVersions.filter { it.tagName !in newVersionTags }

            // Because Assets use Version IDs, and Version IDs may change or be new,
            // it's safest to delete removed assets by name matching, or simply let CASCADE delete
            // assets of deleted versions, and then update existing versions.

            versionsToDelete.forEach { versionDao.delete(it) }

            // Insert/update current data
            // We must update the versions to have the correct repoId from the database
            val versionsToInsert = versionToAssetsMap.keys.map { it.copy(repositoryId = repoId) }
            versionDao.insertMany(versionsToInsert)

            // Refetch inserted versions to get their generated IDs to map to assets
            val insertedVersions = versionDao.getVersionsByRepository(repoId)
            val versionMap = insertedVersions.associateBy { it.tagName }

            val assetsToInsert = mutableListOf<Asset>()
            val assetKeysToKeep = mutableSetOf<Pair<Long, String>>()

            for ((version, assets) in versionToAssetsMap) {
                val insertedVersion = versionMap[version.tagName] ?: continue
                for (asset in assets) {
                    assetsToInsert.add(asset.copy(versionId = insertedVersion.id))
                    assetKeysToKeep.add(Pair(insertedVersion.id, asset.name))
                }
            }

            val assetsToDelete = existingAssets.filter {
                Pair(it.versionId, it.name) !in assetKeysToKeep
            }
            assetsToDelete.forEach { assetDao.delete(it) }

            assetDao.insertMany(assetsToInsert)
        }
    }

    suspend fun getLatestStableVersionWithAssets(repositoryId: Long): VersionWithAssets? {
        val latestVersion = getLatestVersion(
            versionDao.getVersionsByRepository(repositoryId).filter { !it.isPrerelease && it.tagName != IGNORED_TAG },
        )
        return latestVersion?.let { versionDao.getVersionByTagName(repositoryId, it.tagName) }
    }

    suspend fun getLatestBetaVersionWithAssets(repositoryId: Long): VersionWithAssets? {
        val latestVersion = getLatestVersion(
            versionDao.getVersionsByRepository(repositoryId).filter { it.tagName != IGNORED_TAG },
        )
        return latestVersion?.let { versionDao.getVersionByTagName(repositoryId, it.tagName) }
    }

    suspend fun getVersionByTag(repositoryId: Long, tagName: String): VersionWithAssets? =
        versionDao.getVersionByTagName(repositoryId, tagName)

    companion object {
        val SemVerComparator =
            Comparator<Pair<ca.cgagnier.wlednativeandroid.model.Version, com.vdurmont.semver4j.Semver?>> { v1, v2 ->
                val semver1 = v1.second
                val semver2 = v2.second

                if (semver1 != null && semver2 != null) {
                    semver1.compareTo(semver2)
                } else if (semver1 != null) {
                    1
                } else if (semver2 != null) {
                    -1
                } else {
                    v1.first.publishedDate.compareTo(v2.first.publishedDate)
                }
            }

        fun getLatestVersion(
            versions: List<ca.cgagnier.wlednativeandroid.model.Version>,
        ): ca.cgagnier.wlednativeandroid.model.Version? = versions.map {
            it to runCatching {
                com.vdurmont.semver4j.Semver(it.tagName, com.vdurmont.semver4j.Semver.SemverType.LOOSE)
            }.getOrNull()
        }.maxWithOrNull(SemVerComparator)?.first
    }
}
