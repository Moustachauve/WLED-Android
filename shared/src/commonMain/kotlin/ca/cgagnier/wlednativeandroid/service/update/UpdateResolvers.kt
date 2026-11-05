package ca.cgagnier.wlednativeandroid.service.update

import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.model.wledapi.DeviceStateInfo

/**
 * Strategy interface for checking if newer firmware versions exist for a device.
 */
fun interface UpdateTagResolver {
    suspend fun checkForUpdate(device: Device, stateInfo: DeviceStateInfo?): String?
}

/**
 * Strategy interface for resolving repository database identifiers from owner/repo strings.
 */
fun interface RepositoryIdResolver {
    suspend fun getOrCreateRepositoryId(ownerAndRepo: String): Long
}
