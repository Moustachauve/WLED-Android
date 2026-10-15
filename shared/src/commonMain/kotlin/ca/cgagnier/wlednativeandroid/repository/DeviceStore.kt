package ca.cgagnier.wlednativeandroid.repository

import ca.cgagnier.wlednativeandroid.model.Branch
import ca.cgagnier.wlednativeandroid.model.Device
import kotlinx.coroutines.flow.Flow

/**
 * Multiplatform contract for persistent device storage.
 *
 * Supports reactive observation and targeted single-column updates to prevent
 * accidental overwrites of user metadata during concurrent device frames.
 */
interface DeviceStore {
    val allDevices: Flow<List<Device>>
    suspend fun updateOriginalName(macAddress: String, originalName: String)
    suspend fun updateBranch(macAddress: String, branch: Branch)
    suspend fun updateRepositoryId(macAddress: String, repositoryId: Long)
    suspend fun updateLastSeen(macAddress: String, lastSeen: Long)
    suspend fun delete(device: Device)
}
