package ca.cgagnier.wlednativeandroid.widget

import ca.cgagnier.wlednativeandroid.model.WidgetSnapshot
import ca.cgagnier.wlednativeandroid.model.WifiSignalLevel
import kotlinx.serialization.Serializable

@Serializable
data class WidgetStateData(
    val macAddress: String,
    val address: String,
    val name: String,
    val isOn: Boolean,
    val isOnline: Boolean = true,
    val color: Int = -1, // Store as ARGB Int. -1 or default could indicate "unknown"
    val batteryLevel: Int? = null,
    val signalLevel: WifiSignalLevel = if (isOnline) WifiSignalLevel.GOOD else WifiSignalLevel.OFFLINE,
    val lastUpdated: Long = System.currentTimeMillis(),
    // TODO: Add support for quick actions
    val quickActionsEnabled: Boolean = false,
) {
    fun withSnapshot(snapshot: WidgetSnapshot): WidgetStateData = copy(
        macAddress = snapshot.macAddress,
        address = snapshot.address,
        name = snapshot.name,
        isOn = snapshot.isOn,
        isOnline = snapshot.isOnline,
        color = snapshot.color,
        signalLevel = snapshot.signalLevel,
        lastUpdated = snapshot.lastUpdated,
    )

    companion object {
        fun fromSnapshot(snapshot: WidgetSnapshot): WidgetStateData = WidgetStateData(
            macAddress = snapshot.macAddress,
            address = snapshot.address,
            name = snapshot.name,
            isOn = snapshot.isOn,
            isOnline = snapshot.isOnline,
            color = snapshot.color,
            signalLevel = snapshot.signalLevel,
            lastUpdated = snapshot.lastUpdated,
        )
    }
}
