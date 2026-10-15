package ca.cgagnier.wlednativeandroid.service.websocket

/**
 * Structured result containing shaped device lists for UI consumption.
 */
data class PartitionedDevices(
    val visible: List<DeviceWithState>,
    val online: List<DeviceWithState>,
    val offline: List<DeviceWithState>,
)

/**
 * Pure function that shapes, filters, sorts, and partitions devices.
 *
 * Applies:
 * 1. Hidden device filtering according to [showHiddenDevices].
 * 2. Case-insensitive alphabetical sorting based on customName (falling back to originalName).
 * 3. Online/offline partitioning with a grace period [offlineGracePeriodMs] based on device.lastSeen.
 */
fun partitionDevices(
    devices: List<DeviceWithState>,
    showHiddenDevices: Boolean,
    currentTimeMillis: Long,
    offlineGracePeriodMs: Long = 60_000L,
): PartitionedDevices {
    val visible = devices.filter { showHiddenDevices || !it.device.isHidden }
        .sortedWith(
            compareBy(String.CASE_INSENSITIVE_ORDER) {
                it.device.customName.ifBlank { it.device.originalName }
            },
        )
    val (online, offline) = visible.partition { device ->
        device.isOnline || currentTimeMillis - device.device.lastSeen < offlineGracePeriodMs
    }
    return PartitionedDevices(
        visible = visible,
        online = online,
        offline = offline,
    )
}
