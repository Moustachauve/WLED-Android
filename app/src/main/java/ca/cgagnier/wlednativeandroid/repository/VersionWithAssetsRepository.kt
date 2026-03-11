package ca.cgagnier.wlednativeandroid.repository

import androidx.annotation.WorkerThread
import androidx.room.withTransaction
import ca.cgagnier.wlednativeandroid.model.Asset
import ca.cgagnier.wlednativeandroid.model.Version
import ca.cgagnier.wlednativeandroid.model.VersionWithAssets
import javax.inject.Inject

class VersionWithAssetsRepository @Inject constructor(
    private val database: DevicesDatabase,
    private val versionDao: VersionDao,
    private val assetDao: AssetDao,
) {

    @WorkerThread
    suspend fun updateRepository(repository: String, versions: List<Version>, assets: List<Asset>) {
        database.withTransaction {
            // Get existing data for this repository
            val existingVersions = versionDao.getVersionsByRepository(repository)
            val existingAssets = assetDao.getAssetsByRepository(repository)

            // Find versions that were removed from GitHub (in DB but not in new data)
            val newVersionTags = versions.map { it.tagName }.toSet()
            val versionsToDelete = existingVersions.filter { it.tagName !in newVersionTags }

            // Find assets that were removed from GitHub
            val newAssetKeys = assets.map { Triple(it.versionTagName, it.repository, it.name) }.toSet()
            val assetsToDelete = existingAssets.filter {
                Triple(it.versionTagName, it.repository, it.name) !in newAssetKeys
            }

            // Delete only what's been removed
            versionsToDelete.forEach { versionDao.delete(it) }
            assetsToDelete.forEach { assetDao.delete(it) }

            // Insert/update current data (REPLACE strategy handles updates automatically)
            versionDao.insertMany(versions)
            assetDao.insertMany(assets)
        }
    }

    suspend fun getLatestStableVersionWithAssets(repository: String): VersionWithAssets? {
        return versionDao.getLatestStableVersionWithAssets(repository)
    }

    suspend fun getLatestBetaVersionWithAssets(repository: String): VersionWithAssets? {
        return versionDao.getLatestBetaVersionWithAssets(repository)
    }

    suspend fun getVersionByTag(repository: String, tagName: String): VersionWithAssets? {
        return versionDao.getVersionByTagName(repository, tagName)
    }
}
