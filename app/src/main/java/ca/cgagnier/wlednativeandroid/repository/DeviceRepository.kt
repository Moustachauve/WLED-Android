package ca.cgagnier.wlednativeandroid.repository

import androidx.annotation.WorkerThread
import ca.cgagnier.wlednativeandroid.model.Device
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class DeviceRepository @Inject constructor(private val deviceDao: DeviceDao) {
    val allDevices: Flow<List<Device>> = deviceDao.getAlphabetizedDevices()

    @WorkerThread
    fun getAllDevices(): List<Device> = deviceDao.getAllDevices()

    @WorkerThread
    suspend fun findDeviceByMacAddress(address: String): Device? = deviceDao.findDeviceByMacAddress(address)

    @WorkerThread
    suspend fun findDeviceByAddress(address: String): Device? = deviceDao.findDeviceByAddress(address)

    @WorkerThread
    suspend fun insert(device: Device) {
        deviceDao.insert(device)
    }

    @WorkerThread
    suspend fun update(device: Device) {
        deviceDao.update(device)
    }

    @WorkerThread
    suspend fun delete(device: Device) {
        deviceDao.delete(device)
    }

    fun contains(device: Device): Boolean = deviceDao.count(device.address) > 0
}
