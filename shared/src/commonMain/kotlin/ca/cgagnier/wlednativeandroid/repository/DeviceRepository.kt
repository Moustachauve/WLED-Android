package ca.cgagnier.wlednativeandroid.repository

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

    suspend fun delete(device: Device) {
        deviceDao.delete(device)
    }

    suspend fun contains(device: Device): Boolean = deviceDao.count(device.address) > 0
}
