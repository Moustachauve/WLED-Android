package ca.cgagnier.wlednativeandroid.service

import android.util.Log
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.model.wledapi.Info
import ca.cgagnier.wlednativeandroid.repository.DeviceRepository
import ca.cgagnier.wlednativeandroid.service.api.DeviceApiFactory
import java.io.IOException
import javax.inject.Inject

private const val TAG = "DeviceFirstContactService"

/**
 * Service class responsible for handling the first contact with a device.
 */
class DeviceFirstContactService @Inject constructor(
    private val repository: DeviceRepository, private val deviceApiFactory: DeviceApiFactory
) {
    /**
     * Creates a new device record in the database.
     * Assumes the device does not already exist.
     * @param macAddress - The unique MAC address for the new device.
     * @param address - The network address (e.g., IP) for the new device.
     * @param name - The name of the new device.
     * @return The newly created device object.
     */
    private suspend fun createDevice(
        macAddress: String, address: String, name: String
    ): Device {
        Log.d(TAG, "Creating new device entry for MAC: $macAddress at address: $address")
        val device = Device(
            macAddress = macAddress,
            address = address,
            originalName = name,
        )
        repository.insert(device)
        return device
    }

    /**
     * Updates the address of an existing device record in the database.
     * @param device - The existing device object to update.
     * @param newAddress - The new network address for the device.
     * @param name - The new name of the device.
     * @return The updated device object.
     */
    private suspend fun updateDeviceAddress(
        device: Device, newAddress: String, name: String
    ): Device {
        Log.d(TAG, "Updating address for device MAC: ${device.macAddress} to: $newAddress")
        val updatedDevice = device.copy(address = newAddress, originalName = name)
        repository.update(updatedDevice)
        return updatedDevice
    }

    /**
     * Fetches device information from the specified address.
     * @param address - The network address (e.g., IP) to query.
     * @return The device information object.
     */
    private suspend fun getDeviceInfo(address: String): Info {
        return deviceApiFactory.create(address).getInfo().body()
            ?: throw IOException("Response body is null")
    }

    /**
     * Fetches device information using its address, then ensures a corresponding
     * device record exists in the database (creating or updating its address
     * as necessary). Returns the device.
     *
     * @param address - The network address (e.g., IP) to query.
     * @return The device object.
     * @throws Exception if device info cannot be fetched or lacks a MAC address.
     */
    suspend fun fetchAndUpsertDevice(address: String, fromDiscovery: Boolean = false): Device {
        Log.d(TAG, "Trying to create a new device: $address")
        val info = getDeviceInfo(address)

        if (info.macAddress.isNullOrEmpty()) {
            Log.e(TAG, "Could not retrieve MAC address for device at ${address}. Response: $info")
            throw Exception("Could not retrieve MAC address for device at $address")
        }

        val existingDevice = repository.findDeviceByMacAddress(info.macAddress)

        if (existingDevice == null) {
            Log.d(TAG, "No existing device found for MAC: ${info.macAddress}. Creating new entry.")
            return createDevice(info.macAddress, address, info.name)
        }
        if (fromDiscovery && !isIpAddress(existingDevice.address)) {
            Log.i(TAG, "Device already exists for MAC, but ignoring discovery update for non-IP device: ${existingDevice.address}")
            return existingDevice
        }
        if (existingDevice.address == address && existingDevice.originalName == info.name) {
            Log.d(TAG, "Device already exists for MAC and is unchanged: ${info.macAddress}")
            return existingDevice
        }
        Log.d(
            TAG, "Device already exists for MAC but is different: ${existingDevice.macAddress}"
        )
        return updateDeviceAddress(existingDevice, address, info.name)
    }

    /**
     * Attempts to identify and update a device using only the MAC address from mDNS.
     * This avoids a network call to the device if we already know who it is.
     *
     * @param macAddress The MAC address found via mDNS (can be null).
     * @param address The new IP address.
     * @return true if the device was found and processed (updated or skipped), false otherwise.
     */
    suspend fun tryUpdateAddress(macAddress: String?, address: String): Boolean {
        if (macAddress.isNullOrEmpty()) {
            return false
        }
        val existingDevice = repository.findDeviceByMacAddress(macAddress) ?: return false

        // Logic to prevent overwriting non-IP address
        if (!isIpAddress(existingDevice.address)) {
            Log.i(TAG, "Ignoring discovery update for non-IP device: ${existingDevice.address}")
            return true
        }

        // Device is already up to date
        if (existingDevice.address != address) {
            Log.i(TAG, "Fast update: IP changed for ${existingDevice.originalName} ($macAddress)")
            updateDeviceAddress(existingDevice, address, existingDevice.originalName)
        } else {
            Log.d(TAG, "Fast update: Device IP unchanged for $macAddress")
        }
        return true
    }

    private fun isIpAddress(address: String): Boolean {
        val ipv4Pattern = "^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)\\.?\\b){4}\$"
        val ipv6Pattern = "^([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}\$"
        return address.matches(Regex(ipv4Pattern)) || address.matches(Regex(ipv6Pattern))
    }
}