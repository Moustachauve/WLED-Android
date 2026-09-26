package ca.cgagnier.wlednativeandroid.service.update

import android.util.Log
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.model.wledapi.DeviceStateInfo
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import javax.inject.Inject

private const val TAG = "DeviceUpdateManager"

class DeviceUpdateManager @Inject constructor(private val releaseService: ReleaseService) {

    /**
     * Checks if a software update is available for the given [device] and [stateInfo].
     *
     * @return The latest release version tag (e.g., "16.0.1") if an update is available,
     * or `null` if up-to-date, OTA is disabled, or state info is absent.
     */
    suspend fun checkForUpdate(device: Device, stateInfo: DeviceStateInfo?): String? {
        val info = stateInfo?.info ?: return null
        val repository = getRepositoryFromInfo(info)
        Log.d(
            TAG,
            "Checking for software update for ${device.macAddress} on $repository",
        )
        return releaseService.getNewerReleaseTag(
            deviceInfo = info,
            branch = device.branch,
            ignoreVersion = device.skipUpdateTag,
        )
    }

    /**
     * Convenience method to check for updates given an immutable [DeviceWithState].
     */
    suspend fun checkForUpdate(deviceWithState: DeviceWithState): String? =
        checkForUpdate(deviceWithState.device, deviceWithState.stateInfo)
}
