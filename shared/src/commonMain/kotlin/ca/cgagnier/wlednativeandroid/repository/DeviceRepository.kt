package ca.cgagnier.wlednativeandroid.repository

import ca.cgagnier.wlednativeandroid.model.Branch
import ca.cgagnier.wlednativeandroid.model.Device
import kotlinx.coroutines.flow.Flow

class DeviceRepository(private val deviceDao: DeviceDao) {
    val allDevices: Flow<List<Device>> = deviceDao.getAlphabetizedDevices()

    suspend fun getAllDevices(): List<Device> = deviceDao.getAllDevices()

    suspend fun getUsedRepositoryIds(): List<Long> = deviceDao.getUsedRepositoryIds()

    suspend fun findDeviceByMacAddress(address: String): Device? = deviceDao.findDeviceByMacAddress(address)

    suspend fun findDeviceByAddress(address: String): Device? = deviceDao.findDeviceByAddress(address)

    suspend fun insert(device: Device) {
        deviceDao.insert(device)
    }

    suspend fun update(device: Device) {
        deviceDao.update(device)
    }

    suspend fun updateAddress(macAddress: String, address: String) = deviceDao.updateAddress(macAddress, address)

    suspend fun updateOriginalName(macAddress: String, originalName: String) =
        deviceDao.updateOriginalName(macAddress, originalName)

    suspend fun updateCustomName(macAddress: String, customName: String) =
        deviceDao.updateCustomName(macAddress, customName)

    suspend fun updateIsHidden(macAddress: String, isHidden: Boolean) = deviceDao.updateIsHidden(macAddress, isHidden)

    suspend fun updateSkipUpdateTag(macAddress: String, skipUpdateTag: String) =
        deviceDao.updateSkipUpdateTag(macAddress, skipUpdateTag)

    /** Updates the branch and clears the skipped version, since it belongs to the previous branch. */
    suspend fun updateBranch(macAddress: String, branch: Branch) = deviceDao.updateBranch(macAddress, branch)

    suspend fun updateLastSeen(macAddress: String, lastSeen: Long) = deviceDao.updateLastSeen(macAddress, lastSeen)

    suspend fun delete(device: Device) {
        deviceDao.delete(device)
    }

    suspend fun contains(device: Device): Boolean = deviceDao.count(device.address) > 0
}
