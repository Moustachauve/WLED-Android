package ca.cgagnier.wlednativeandroid.service

import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.model.wledapi.Info
import ca.cgagnier.wlednativeandroid.repository.DeviceRepository
import ca.cgagnier.wlednativeandroid.repository.RepositoryDao
import ca.cgagnier.wlednativeandroid.repository.getOrCreateRepositoryId
import ca.cgagnier.wlednativeandroid.service.api.DeviceApiFactory
import ca.cgagnier.wlednativeandroid.service.update.getRepositoryFromInfo
import ca.cgagnier.wlednativeandroid.util.isIpAddress
import ca.cgagnier.wlednativeandroid.util.sanitizeAddress
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException

private const val TAG = "DeviceFirstContactService"
private val logger = Logger.withTag(TAG)

/**
 * Failures of the first contact with a device.
 */
sealed class DeviceFirstContactException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class MissingMacAddress(val address: String) :
        DeviceFirstContactException("Could not retrieve MAC address for device at $address")

    class HttpError(val address: String, val statusCode: Int) :
        DeviceFirstContactException("Device at $address responded with HTTP $statusCode")

    class RequestFailed(val address: String, cause: Throwable) :
        DeviceFirstContactException("Request to device at $address failed: ${cause.message}", cause)
}

/**
 * Service responsible for handling the first contact with a device.
 * Fetches device info and handles the creation or update of the [Device] record in the repository.
 */
class DeviceFirstContactService(
    private val repositoryDao: RepositoryDao,
    private val repository: DeviceRepository,
    private val deviceApiFactory: DeviceApiFactory,
    private val onDeviceAddressUpdated: suspend (Device) -> Unit = {},
) {
    constructor(
        repositoryDao: RepositoryDao,
        repository: DeviceRepository,
        deviceApiFactory: DeviceApiFactory,
    ) : this(repositoryDao, repository, deviceApiFactory, {})

    /**
     * Fetches device information using its address, then ensures a corresponding
     * device record exists in the database (creating or updating its address
     * as necessary). Returns the device.
     *
     * @param rawAddress The network address input (e.g., "http://192.168.1.1/" or "wled.local").
     * @return The created or updated [Device].
     * @throws DeviceFirstContactException if device info cannot be fetched or lacks a MAC address.
     */
    @Throws(DeviceFirstContactException::class, CancellationException::class)
    suspend fun fetchAndUpsertDevice(rawAddress: String): Device {
        val cleanAddress = rawAddress.sanitizeAddress()
        logger.d { "Trying to contact device at: $cleanAddress" }
        val info = fetchDeviceInfo(cleanAddress)

        val macAddress = info.macAddress
        if (macAddress.isNullOrEmpty()) {
            logger.e { "Could not retrieve MAC address for device at $cleanAddress. Response: $info" }
            throw DeviceFirstContactException.MissingMacAddress(cleanAddress)
        }

        val existingDevice = repository.findDeviceByMacAddress(macAddress)
        if (existingDevice == null) {
            logger.d { "No existing device found for MAC: $macAddress. Creating new entry." }
            return createDevice(macAddress, cleanAddress, info)
        }
        return updateExistingDevice(existingDevice, cleanAddress, info)
    }

    /**
     * Attempts to identify and update a device using only the MAC address from mDNS/Discovery.
     * This avoids a network call to the device if we already know who it is.
     *
     * @param macAddress The MAC address found via mDNS (can be null/empty).
     * @param rawAddress The new IP address or hostname.
     * @return true if the device was found and processed (updated or skipped), false otherwise.
     */
    suspend fun tryUpdateAddress(macAddress: String?, rawAddress: String): Boolean {
        if (macAddress.isNullOrEmpty()) {
            return false
        }
        val cleanAddress = rawAddress.sanitizeAddress()
        val existingDevice = repository.findDeviceByMacAddress(macAddress) ?: return false

        val effectiveAddress = resolveEffectiveAddress(existingDevice.address, cleanAddress)
        if (existingDevice.address != effectiveAddress) {
            logger.i { "Fast update: IP changed for ${existingDevice.originalName} ($macAddress)" }
            try {
                repository.updateAddress(existingDevice.macAddress, effectiveAddress)
                onDeviceAddressUpdated(existingDevice.copy(address = effectiveAddress))
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                logger.e(e) { "Failed to save fast update for $macAddress" }
            }
        } else {
            logger.d { "Fast update: Device address unchanged for $macAddress" }
        }
        return true
    }

    private suspend fun createDevice(macAddress: String, address: String, info: Info): Device {
        logger.d { "Creating new device entry for MAC: $macAddress at address: $address" }
        val deviceRepositoryStr = getRepositoryFromInfo(info)
        val repoId = repositoryDao.getOrCreateRepositoryId(deviceRepositoryStr)

        val device = Device(
            macAddress = macAddress,
            address = address,
            originalName = info.name,
            repositoryId = repoId,
        )
        repository.insert(device)
        return device
    }

    private suspend fun updateExistingDevice(existingDevice: Device, newAddress: String, info: Info): Device {
        val effectiveAddress = resolveEffectiveAddress(existingDevice.address, newAddress)
        val deviceRepositoryStr = getRepositoryFromInfo(info)
        val repoId = repositoryDao.getOrCreateRepositoryId(deviceRepositoryStr)

        val addressChanged = existingDevice.address != effectiveAddress
        val nameChanged = existingDevice.originalName != info.name
        val repoChanged = existingDevice.repositoryId != repoId

        if (!addressChanged && !nameChanged && !repoChanged) {
            logger.d { "Device already exists for MAC and is unchanged: ${existingDevice.macAddress}" }
            return existingDevice
        }

        logger.d { "Updating existing device for MAC: ${existingDevice.macAddress}" }
        val mac = existingDevice.macAddress
        if (addressChanged) {
            repository.updateAddress(mac, effectiveAddress)
        }
        if (nameChanged) {
            repository.updateOriginalName(mac, info.name)
        }
        if (repoChanged) {
            repository.updateRepositoryId(mac, repoId)
        }

        val updatedDevice = existingDevice.copy(
            address = effectiveAddress,
            originalName = info.name,
            repositoryId = repoId,
        )
        onDeviceAddressUpdated(updatedDevice)
        return updatedDevice
    }

    /**
     * Keeps user-defined hostnames (e.g. "wled.local") and only updates the stored address if the
     * existing address is an IP literal. This avoids overriding a hostname that may be reachable
     * across networks.
     */
    private fun resolveEffectiveAddress(existingAddress: String, newAddress: String): String =
        if (existingAddress.isIpAddress()) newAddress else existingAddress

    @Suppress("ThrowsCount")
    private suspend fun fetchDeviceInfo(address: String): Info {
        val response = try {
            deviceApiFactory.create(address).getInfo()
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            throw DeviceFirstContactException.RequestFailed(address, e)
        }

        val info = response.body
        if (!response.isSuccessful || info == null) {
            logger.e { "Device at $address responded with HTTP ${response.code}" }
            throw DeviceFirstContactException.HttpError(address, response.code)
        }
        return info
    }
}
