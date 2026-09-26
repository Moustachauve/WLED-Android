package ca.cgagnier.wlednativeandroid.domain.usecase

import android.util.Log
import ca.cgagnier.wlednativeandroid.di.IoDispatcher
import ca.cgagnier.wlednativeandroid.model.Branch
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.model.Repository
import ca.cgagnier.wlednativeandroid.model.wledapi.DeviceStateInfo
import ca.cgagnier.wlednativeandroid.repository.DeviceRepository
import ca.cgagnier.wlednativeandroid.repository.RepositoryDao
import ca.cgagnier.wlednativeandroid.repository.getOrCreateRepositoryId
import ca.cgagnier.wlednativeandroid.service.update.getRepositoryFromInfo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Domain use case to persist updated device metadata to the database when changes
 * are detected from an inbound DeviceStateInfo update or when the lastSeen threshold expires.
 */
class SaveDeviceStateUseCase @Inject constructor(
    private val deviceRepository: DeviceRepository,
    private val repositoryDao: RepositoryDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    companion object {
        private const val TAG = "SaveDeviceStateUseCase"
        const val LAST_SEEN_UPDATE_THRESHOLD = 15 * 60 * 1000L // 15 minutes
    }

    /**
     * Evaluates stateInfo against currentDevice and persists updates if changed.
     *
     * @param currentDevice The existing device model.
     * @param stateInfo The latest state information received from WLED.
     * @param currentTimeMillis Clock timestamp, defaulting to System.currentTimeMillis() for testability.
     * @return The updated Device saved to DB, or null if no update was required.
     */
    suspend operator fun invoke(
        currentDevice: Device,
        stateInfo: DeviceStateInfo,
        currentTimeMillis: Long = System.currentTimeMillis(),
    ): Device? = withContext(ioDispatcher) {
        var branch = currentDevice.branch
        if (branch == Branch.UNKNOWN) {
            branch = if (stateInfo.info.version?.contains("-b") == true) {
                Branch.BETA
            } else {
                Branch.STABLE
            }
        }

        val nameChanged = currentDevice.originalName != stateInfo.info.name
        val branchChanged = currentDevice.branch != branch
        val timeSinceLastUpdate = currentTimeMillis - currentDevice.lastSeen
        val timeThresholdExceeded = timeSinceLastUpdate > LAST_SEEN_UPDATE_THRESHOLD

        val repositoryStr = getRepositoryFromInfo(stateInfo.info)
        val isDefaultRepo = repositoryStr == Repository.DEFAULT_OWNER_REPO &&
            currentDevice.repositoryId == Repository.DEFAULT_ID

        val needsPersistence = nameChanged || branchChanged || timeThresholdExceeded
        if (!needsPersistence && isDefaultRepo) {
            return@withContext null
        }

        val repoIdToSave = if (isDefaultRepo) {
            Repository.DEFAULT_ID
        } else {
            repositoryDao.getOrCreateRepositoryId(repositoryStr)
        }
        val repositoryChanged = currentDevice.repositoryId != repoIdToSave

        val shouldUpdateDevice = needsPersistence || repositoryChanged

        if (shouldUpdateDevice) {
            val newDevice = currentDevice.copy(
                originalName = stateInfo.info.name,
                address = currentDevice.address,
                lastSeen = currentTimeMillis,
                branch = branch,
                repositoryId = repoIdToSave,
            )
            deviceRepository.update(newDevice)
            Log.d(TAG, "Device persisted to DB: ${newDevice.address}")
            newDevice
        } else {
            null
        }
    }
}
