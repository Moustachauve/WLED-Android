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
    private val assetDao: AssetDao
) {

    @WorkerThread
    suspend fun replaceAll(versions: List<Version>, assets: List<Asset>) {
        database.withTransaction {
            versionDao.deleteAll()
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