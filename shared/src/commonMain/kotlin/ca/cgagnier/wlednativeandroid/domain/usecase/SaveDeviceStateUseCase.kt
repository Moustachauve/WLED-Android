package ca.cgagnier.wlednativeandroid.domain.usecase

import ca.cgagnier.wlednativeandroid.model.Branch
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.model.Repository
import ca.cgagnier.wlednativeandroid.model.wledapi.DeviceStateInfo
import ca.cgagnier.wlednativeandroid.repository.DeviceRepository
import ca.cgagnier.wlednativeandroid.repository.RepositoryDao
import ca.cgagnier.wlednativeandroid.repository.getOrCreateRepositoryId
import ca.cgagnier.wlednativeandroid.service.update.getRepositoryFromInfo
import ca.cgagnier.wlednativeandroid.shared.currentTimeMillis
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val TAG = "SaveDeviceStateUseCase"
private val logger = Logger.withTag(TAG)

/**
 * Domain use case to persist updated device metadata to the database when changes
 * are detected from an inbound DeviceStateInfo update or when the lastSeen threshold expires.
 */
class SaveDeviceStateUseCase(
    private val deviceRepository: DeviceRepository,
    private val repositoryDao: RepositoryDao,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    companion object {
        const val LAST_SEEN_UPDATE_THRESHOLD = 15 * 60 * 1000L // 15 minutes
    }

    private val cacheMutex = Mutex()
    private val repositoryIdCache = mutableMapOf<String, Long>(
        Repository.DEFAULT_OWNER_REPO to Repository.DEFAULT_ID,
    )

    /**
     * Evaluates stateInfo against currentDevice and persists updates if changed.
     *
     * @param currentDevice The existing device model.
     * @param stateInfo The latest state information received from WLED.
     * @param currentTimeMillis Clock timestamp, defaulting to currentTimeMillis() for testability.
     * @return The updated Device saved to DB, or null if no update was required.
     */
    suspend operator fun invoke(
        currentDevice: Device,
        stateInfo: DeviceStateInfo,
        currentTimeMillis: Long = currentTimeMillis(),
    ): Device? = withContext(dispatcher) {
        val branch = inferBranch(currentDevice.branch, stateInfo.info.version)
        val hasValidName = stateInfo.info.name.isNotBlank()
        val nameChanged = hasValidName && currentDevice.originalName != stateInfo.info.name
        val branchChanged = currentDevice.branch != branch
        val timeSinceLastUpdate = currentTimeMillis - currentDevice.lastSeen
        val timeThresholdExceeded = timeSinceLastUpdate > LAST_SEEN_UPDATE_THRESHOLD

        val repositoryStr = getRepositoryFromInfo(stateInfo.info)
        val isDefaultRepo = repositoryStr == Repository.DEFAULT_OWNER_REPO &&
            currentDevice.repositoryId == Repository.DEFAULT_ID
        val cachedRepoId = cacheMutex.withLock { repositoryIdCache[repositoryStr] }
        val isRepoUnchanged = isDefaultRepo || (cachedRepoId != null && cachedRepoId == currentDevice.repositoryId)

        val needsPersistence = nameChanged || branchChanged || timeThresholdExceeded
        if (!needsPersistence && isRepoUnchanged) {
            return@withContext null
        }

        val repoIdToSave = resolveRepositoryId(repositoryStr, isDefaultRepo)
        val repositoryChanged = currentDevice.repositoryId != repoIdToSave

        val shouldUpdateDevice = needsPersistence || repositoryChanged

        if (shouldUpdateDevice) {
            val newDevice = currentDevice.copy(
                originalName = if (hasValidName) stateInfo.info.name else currentDevice.originalName,
                address = currentDevice.address,
                lastSeen = currentTimeMillis,
                branch = branch,
                repositoryId = repoIdToSave,
            )
            deviceRepository.update(newDevice)
            logger.d { "Device persisted to DB: ${newDevice.address}" }
            newDevice
        } else {
            null
        }
    }

    private fun inferBranch(currentBranch: Branch, version: String?): Branch {
        if (currentBranch != Branch.UNKNOWN || version.isNullOrBlank()) {
            return currentBranch
        }
        return if (version.contains("-b")) Branch.BETA else Branch.STABLE
    }

    private suspend fun resolveRepositoryId(repositoryStr: String, isDefaultRepo: Boolean): Long {
        if (isDefaultRepo) return Repository.DEFAULT_ID
        cacheMutex.withLock { repositoryIdCache[repositoryStr] }?.let { return it }

        val id = repositoryDao.getOrCreateRepositoryId(repositoryStr)
        cacheMutex.withLock {
            repositoryIdCache[repositoryStr] = id
        }
        return id
    }
}
